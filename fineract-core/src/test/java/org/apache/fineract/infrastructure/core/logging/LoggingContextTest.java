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
package org.apache.fineract.infrastructure.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LoggingContextTest {

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void setTenantIdPutsTheTenantIntoTheMdc() {
        LoggingContext.setTenantId("default");

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isEqualTo("default");
    }

    @Test
    void setTenantIdWithoutAValueRemovesTheTenant() {
        LoggingContext.setTenantId("default");

        LoggingContext.setTenantId(null);
        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();

        LoggingContext.setTenantId("default");
        LoggingContext.setTenantId("  ");
        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
    }

    @Test
    void clearTenantIdRemovesOnlyTheTenant() {
        LoggingContext.setTenantId("default");
        LoggingContext.setLoanId(42L);

        LoggingContext.clearTenantId();

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");
    }

    @Test
    void setLoanIdPutsAndRemovesTheLoan() {
        LoggingContext.setLoanId(42L);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");

        LoggingContext.setLoanId(null);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void withLoanIdSetsTheLoanUntilTheScopeIsClosed() {
        final LoggingContext.Scope scope = LoggingContext.withLoanId(42L);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");

        scope.close();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void closingANestedScopeRestoresTheOuterLoan() {
        final LoggingContext.Scope outer = LoggingContext.withLoanId(1L);
        final LoggingContext.Scope inner = LoggingContext.withLoanId(2L);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("2");

        inner.close();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("1");

        outer.close();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void withLoanIdWithoutALoanLeavesTheMdcAlone() {
        LoggingContext.setLoanId(1L);

        final LoggingContext.Scope scope = LoggingContext.withLoanId(null);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("1");

        scope.close();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("1");
    }

    @Test
    void clearRemovesTenantAndLoanButKeepsTheCorrelationId() {
        MDC.put(LoggingContext.CORRELATION_ID, "abc-123");
        LoggingContext.setTenantId("default");
        LoggingContext.setLoanId(42L);

        LoggingContext.clear();

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
        // the correlation id belongs to CorrelationHeaderFilter, which removes it when the request is over
        assertThat(MDC.get(LoggingContext.CORRELATION_ID)).isEqualTo("abc-123");
    }

    @Test
    void aSnapshotPutsTenantAndLoanBackAfterAResetUntilItsScopeIsClosed() {
        MDC.put(LoggingContext.CORRELATION_ID, "abc-123");
        LoggingContext.setTenantId("default");
        LoggingContext.setLoanId(42L);

        final Map<String, String> snapshot = LoggingContext.snapshot();
        LoggingContext.clear();
        final LoggingContext.Scope scope = LoggingContext.restore(snapshot);

        assertThat(snapshot).containsOnly(Map.entry(LoggingContext.TENANT_ID, "default"), Map.entry(LoggingContext.LOAN_ID, "42"));
        assertThat(MDC.get(LoggingContext.TENANT_ID)).isEqualTo("default");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");

        scope.close();

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
        assertThat(MDC.get(LoggingContext.CORRELATION_ID)).isEqualTo("abc-123");
    }

    @Test
    void restoringAnEmptySnapshotPutsNothingOnTheThread() {
        final LoggingContext.Scope scope = LoggingContext.restore(LoggingContext.snapshot());

        assertThat(MDC.get(LoggingContext.TENANT_ID)).isNull();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
        scope.close();
    }

    @Test
    void theMdcKeysAreTheFieldNamesOfTheJsonLogLine() {
        assertThat(LoggingContext.TENANT_ID).isEqualTo("tenantId");
        assertThat(LoggingContext.CORRELATION_ID).isEqualTo("correlationId");
        assertThat(LoggingContext.LOAN_ID).isEqualTo("loanId");
    }
}
