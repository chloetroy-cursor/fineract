/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.logging.LoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ThreadLocalContextUtilLoggingContextTest {

    private static final FineractPlatformTenant DEFAULT_TENANT = new FineractPlatformTenant(1L, "default", "Default", "UTC", null);
    private static final FineractPlatformTenant OTHER_TENANT = new FineractPlatformTenant(2L, "other", "Other", "UTC", null);

    @BeforeEach
    @AfterEach
    void clearContext() {
        ThreadLocalContextUtil.reset();
        MDC.clear();
    }

    @Test
    void setTenantPutsTheTenantIdentifierIntoTheMdc() {
        ThreadLocalContextUtil.setTenant(DEFAULT_TENANT);
        assertThat(MDC.get(LoggingContext.TENANT_ID)).isEqualTo("default");

        ThreadLocalContextUtil.setTenant(OTHER_TENANT);
        assertThat(MDC.get(LoggingContext.TENANT_ID)).isEqualTo("other");
    }

    @Test
    void setTenantWithoutATenantRemovesItFromTheMdc() {
        ThreadLocalContextUtil.setTenant(DEFAULT_TENANT);

        ThreadLocalContextUtil.setTenant(null);

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
    }

    @Test
    void clearTenantRemovesItFromTheMdc() {
        ThreadLocalContextUtil.setTenant(DEFAULT_TENANT);

        ThreadLocalContextUtil.clearTenant();

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
    }

    @Test
    void resetRemovesTenantAndLoanFromTheMdc() {
        ThreadLocalContextUtil.setTenant(DEFAULT_TENANT);
        LoggingContext.setLoanId(42L);

        ThreadLocalContextUtil.reset();

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void initOnAWorkerThreadPutsTheTenantIntoThatThreadsMdcAndResetLeavesNothingForTheNextTask() throws Exception {
        final FineractContext context = new FineractContext("default", OTHER_TENANT, "token",
                new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 1, 15))), ActionContext.COB);
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            final Map<String, String> duringTask = worker.submit(() -> {
                ThreadLocalContextUtil.init(context);
                LoggingContext.setLoanId(42L);
                try {
                    return MDC.getCopyOfContextMap();
                } finally {
                    ThreadLocalContextUtil.reset();
                }
            }).get(10, TimeUnit.SECONDS);
            final Map<String, String> nextTaskOnTheSameThread = worker.submit(MDC::getCopyOfContextMap).get(10, TimeUnit.SECONDS);

            assertThat(duringTask).containsEntry(LoggingContext.TENANT_ID, "other").containsEntry(LoggingContext.LOAN_ID, "42");
            assertThat(nextTaskOnTheSameThread == null ? Map.<String, String>of() : nextTaskOnTheSameThread)
                    .doesNotContainKeys(LoggingContext.TENANT_ID, LoggingContext.LOAN_ID);
            // nothing of the worker's context shows up on the submitting thread
            assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
        } finally {
            worker.shutdownNow();
        }
    }
}
