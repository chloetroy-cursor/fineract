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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

/**
 * The MDC keys Fineract puts on log lines, and the only place that writes the tenant and loan ones.
 * <p>
 * These identifiers are for logs only. Loan and client ids are unbounded, so they must never become metric tags.
 */
public final class LoggingContext {

    public static final String TENANT_ID = "tenantId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String LOAN_ID = "loanId";

    private static final List<String> UNIT_OF_WORK_KEYS = List.of(TENANT_ID, LOAN_ID);

    private LoggingContext() {}

    public static void setTenantId(final String tenantIdentifier) {
        putOrRemove(TENANT_ID, tenantIdentifier);
    }

    public static void clearTenantId() {
        MDC.remove(TENANT_ID);
    }

    public static void setLoanId(final Long loanId) {
        putOrRemove(LOAN_ID, loanId == null ? null : String.valueOf(loanId));
    }

    /**
     * Puts the loan id on the current thread until the returned scope is closed. Closing restores the loan id that was
     * there before, so a loan command that runs inside a loan API request, or inside a batch of commands, leaves the
     * outer value in place.
     *
     * @param loanId
     *            the loan being worked on, or {@code null} when the work is not about one loan
     * @return the scope to close, never {@code null}
     */
    public static Scope withLoanId(final Long loanId) {
        if (loanId == null) {
            return Scope.NOOP;
        }
        final String previous = MDC.get(LOAN_ID);
        MDC.put(LOAN_ID, String.valueOf(loanId));
        return () -> putOrRemove(LOAN_ID, previous);
    }

    /**
     * Removes what belongs to one unit of work on this thread: the tenant and the loan. The correlation id is left
     * alone, it belongs to the request and is removed by the filter that set it.
     */
    public static void clear() {
        UNIT_OF_WORK_KEYS.forEach(MDC::remove);
    }

    /**
     * The tenant and loan id that are on this thread now, to be put back with {@link #restore(Map)}. For the lines a
     * request logs about itself after its thread context has been reset.
     */
    public static Map<String, String> snapshot() {
        final Map<String, String> snapshot = new HashMap<>();
        for (String key : UNIT_OF_WORK_KEYS) {
            final String value = MDC.get(key);
            if (value != null) {
                snapshot.put(key, value);
            }
        }
        return snapshot;
    }

    /**
     * Puts the tenant and loan id of a {@link #snapshot()} back on this thread until the returned scope is closed.
     * Closing removes them, it does not bring back what was there in between.
     */
    public static Scope restore(final Map<String, String> snapshot) {
        for (String key : UNIT_OF_WORK_KEYS) {
            putOrRemove(key, snapshot.get(key));
        }
        return LoggingContext::clear;
    }

    private static void putOrRemove(final String key, final String value) {
        if (StringUtils.isBlank(value)) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        Scope NOOP = () -> {
            // nothing was set, so there is nothing to restore
        };

        @Override
        void close();
    }
}
