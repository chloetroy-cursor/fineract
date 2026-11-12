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
package org.apache.fineract.cob;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Meters for the COB business step chain run by {@link COBBusinessStepServiceImpl}.
 *
 * Names, tag keys and tag values are fixed by the "Metric naming and cardinality" chapter of the architecture
 * documentation. The only tags are {@code step} and {@code outcome}: nothing here may identify a loan, a client or a
 * tenant.
 *
 * The timer counts calls to a step, so a retried step is one sample for each attempt. The counter counts items by what
 * became of their transaction, see {@link #recordItem(AbstractPersistableCustom, boolean)}.
 *
 * The recording methods never throw, so a metrics problem cannot fail a business step or replace its exception.
 */
@Slf4j
@Component
public class COBBusinessStepMetrics {

    public static final String STEP_EXECUTION_TIMER = "fineract.cob.step.execution";
    public static final String LOANS_PROCESSED_COUNTER = "fineract.cob.loans.processed";
    public static final String TAG_STEP = "step";
    public static final String TAG_OUTCOME = "outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Counter loansSucceeded;
    private final Counter loansFailed;
    // COB runs every step for every loan, so the timers are looked up by step name instead of being rebuilt per call.
    // A timer is registered the first time its step and outcome are recorded, which keeps the failure series of a
    // step that never fails out of the registry.
    private final ConcurrentMap<String, Timer> succeededStepTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> failedStepTimers = new ConcurrentHashMap<>();
    private final AtomicBoolean recordingFailureLogged = new AtomicBoolean();

    public COBBusinessStepMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.clock = meterRegistry.config().clock();
        this.loansSucceeded = loansProcessedCounter(OUTCOME_SUCCESS);
        this.loansFailed = loansProcessedCounter(OUTCOME_FAILURE);
    }

    /**
     * Reads the clock just before a call to {@link COBBusinessStep#execute}.
     *
     * @return the start time to hand to {@link #recordStep(COBBusinessStep, long, boolean)}
     */
    public long startStep() {
        return clock.monotonicTime();
    }

    /**
     * Records one call to {@link COBBusinessStep#execute}.
     *
     * @param step
     *            the business step that was executed
     * @param startedAt
     *            the value {@link #startStep()} returned just before the call
     * @param succeeded
     *            {@code true} when {@code execute} returned, {@code false} when it threw
     */
    public void recordStep(COBBusinessStep<?> step, long startedAt, boolean succeeded) {
        try {
            long elapsedNanos = clock.monotonicTime() - startedAt;
            String stepName = step.getEnumStyledName();
            if (stepName == null || stepName.isBlank()) {
                return;
            }
            stepTimer(stepName, succeeded).record(elapsedNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException e) {
            logRecordingFailure(e);
        }
    }

    /**
     * Counts one item that went through the business step chain.
     *
     * Inside a transaction the item is counted when that transaction completes: as {@code success} when the chain
     * returned and the transaction committed, as {@code failure} otherwise. An item that is retried inside the
     * transaction is counted once, by its last attempt. Outside a transaction the item is counted at once.
     *
     * @param item
     *            the item handed to the chain; its id only tells a retry from another item and is never a tag
     * @param succeeded
     *            {@code true} when every configured step returned, {@code false} when the chain ended early
     */
    public void recordItem(AbstractPersistableCustom<Long> item, boolean succeeded) {
        try {
            ItemsOfTransaction itemsOfTransaction = itemsOfCurrentTransaction();
            if (itemsOfTransaction == null) {
                count(succeeded, 1);
            } else {
                itemsOfTransaction.put(item, succeeded);
            }
        } catch (RuntimeException e) {
            logRecordingFailure(e);
        }
    }

    // The holder is looked up among the synchronizations of the transaction instead of being bound to the thread, so
    // Spring suspends, resumes and discards it with the transaction and nothing here can outlive it.
    private ItemsOfTransaction itemsOfCurrentTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof ItemsOfTransaction itemsOfTransaction && itemsOfTransaction.belongsTo(this)) {
                return itemsOfTransaction;
            }
        }
        ItemsOfTransaction itemsOfTransaction = new ItemsOfTransaction();
        TransactionSynchronizationManager.registerSynchronization(itemsOfTransaction);
        return itemsOfTransaction;
    }

    private void count(boolean succeeded, int items) {
        if (items > 0) {
            (succeeded ? loansSucceeded : loansFailed).increment(items);
        }
    }

    private final class ItemsOfTransaction implements TransactionSynchronization {

        private final Map<Object, Boolean> chainReturnedByItem = new HashMap<>();

        private boolean belongsTo(COBBusinessStepMetrics metrics) {
            return COBBusinessStepMetrics.this == metrics;
        }

        private void put(AbstractPersistableCustom<Long> item, boolean chainReturned) {
            chainReturnedByItem.put(key(item), chainReturned);
        }

        private Object key(AbstractPersistableCustom<Long> item) {
            if (item == null) {
                return new Object();
            }
            Long id = item.getId();
            return id == null ? item : new ItemKey(item.getClass(), id);
        }

        @Override
        public void afterCompletion(int status) {
            try {
                boolean committed = status == STATUS_COMMITTED;
                int succeeded = 0;
                for (boolean chainReturned : chainReturnedByItem.values()) {
                    if (committed && chainReturned) {
                        succeeded++;
                    }
                }
                count(true, succeeded);
                count(false, chainReturnedByItem.size() - succeeded);
            } catch (RuntimeException e) {
                logRecordingFailure(e);
            }
        }
    }

    private record ItemKey(Class<?> type, Long id) {
    }

    private Timer stepTimer(String stepName, boolean succeeded) {
        ConcurrentMap<String, Timer> timers = succeeded ? succeededStepTimers : failedStepTimers;
        Timer timer = timers.get(stepName);
        if (timer == null) {
            timer = timers.computeIfAbsent(stepName,
                    name -> Timer.builder(STEP_EXECUTION_TIMER).description("Time taken by one COB business step for one loan")
                            .tag(TAG_STEP, name).tag(TAG_OUTCOME, succeeded ? OUTCOME_SUCCESS : OUTCOME_FAILURE).register(meterRegistry));
        }
        return timer;
    }

    private Counter loansProcessedCounter(String outcome) {
        return Counter.builder(LOANS_PROCESSED_COUNTER).description("Loans taken through the COB business step chain")
                .tag(TAG_OUTCOME, outcome).register(meterRegistry);
    }

    private void logRecordingFailure(RuntimeException e) {
        if (recordingFailureLogged.compareAndSet(false, true)) {
            log.warn("Recording COB business step metrics failed; further failures are logged at debug level", e);
        } else {
            log.debug("Recording COB business step metrics failed", e);
        }
    }
}
