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
package org.apache.fineract.commands.service;

import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_APPROVE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_CREATE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_DISBURSE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_DISBURSETOSAVINGS;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_DISBURSEWITHOUTAUTODOWNPAYMENT;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_REPAYMENT;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ENTITY_LOAN;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.apache.fineract.infrastructure.core.exception.AbstractIdempotentCommandException;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The loan command meters: a timer and a counter for each loan submit, approve, disburse and repay command that
 * {@link SynchronousCommandProcessingService#executeCommand} runs, tagged by {@code command} and {@code outcome}.
 * <p>
 * Both tags come from the closed sets {@link LoanCommand} and {@link Outcome}. Nothing that identifies a loan, a
 * client, a tenant or a request is ever a tag value.
 */
@Slf4j
@Component
public class LoanCommandMetrics {

    public static final String TIMER_NAME = "fineract.loan.command.execution";
    public static final String COUNTER_NAME = "fineract.loan.commands";
    public static final String TAG_COMMAND = "command";
    public static final String TAG_OUTCOME = "outcome";

    private static final String TIMER_DESCRIPTION = "Time taken to execute a loan command, including retries";
    private static final String COUNTER_DESCRIPTION = "Loan commands executed";

    private static final LoanCommand[] COMMANDS = LoanCommand.values();
    private static final Outcome[] OUTCOMES = Outcome.values();

    public enum LoanCommand {

        SUBMIT("submit"), APPROVE("approve"), DISBURSE("disburse"), REPAY("repay");

        private final String tagValue;

        LoanCommand(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }

        /**
         * @return the loan command a wrapper stands for, or {@code null} when the command is not one of the recorded
         *         loan commands
         */
        public static LoanCommand of(CommandWrapper wrapper) {
            if (wrapper == null || !ENTITY_LOAN.equals(wrapper.entityName())) {
                return null;
            }
            final String actionName = wrapper.actionName();
            if (actionName == null) {
                return null;
            }
            return switch (actionName) {
                case ACTION_CREATE -> SUBMIT;
                case ACTION_APPROVE -> APPROVE;
                case ACTION_DISBURSE, ACTION_DISBURSETOSAVINGS, ACTION_DISBURSEWITHOUTAUTODOWNPAYMENT -> DISBURSE;
                case ACTION_REPAYMENT -> REPAY;
                default -> null;
            };
        }
    }

    public enum Outcome {

        SUCCESS("success"), //
        AWAITING_APPROVAL("awaiting_approval"), //
        DUPLICATE("duplicate"), //
        CLIENT_ERROR("client_error"), //
        SERVER_ERROR("server_error");

        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    private final MeterRegistry registry;
    private final Clock clock;
    private final ErrorHandler errorHandler;
    private final Counter[] counters = new Counter[COMMANDS.length * OUTCOMES.length];
    // A timer costs 12 series for each tag combination, so it is registered when the combination is first recorded
    private final AtomicReferenceArray<Timer> timers = new AtomicReferenceArray<>(COMMANDS.length * OUTCOMES.length);

    @Autowired
    public LoanCommandMetrics(ObjectProvider<MeterRegistry> registry, ErrorHandler errorHandler) {
        this(registry.getIfAvailable(() -> Metrics.globalRegistry), errorHandler);
    }

    public LoanCommandMetrics(MeterRegistry registry, ErrorHandler errorHandler) {
        this.registry = registry;
        this.clock = registry.config().clock();
        this.errorHandler = errorHandler;
        for (LoanCommand command : COMMANDS) {
            for (Outcome outcome : OUTCOMES) {
                counters[index(command, outcome)] = Counter.builder(COUNTER_NAME).description(COUNTER_DESCRIPTION)
                        .tag(TAG_COMMAND, command.tagValue()).tag(TAG_OUTCOME, outcome.tagValue()).register(registry);
            }
        }
    }

    /**
     * @return the start time to hand back to {@link #recordSuccess} or {@link #recordFailure}
     */
    public long start() {
        return clock.monotonicTime();
    }

    public void recordSuccess(LoanCommand command, long startNanos) {
        recordSample(command, Outcome.SUCCESS, startNanos);
    }

    /**
     * @param httpStatus
     *            the HTTP status {@link ErrorHandler#handle} already gave for {@code thrown}, or {@code null} to have
     *            it worked out here
     */
    public void recordFailure(LoanCommand command, long startNanos, Throwable thrown, Integer httpStatus) {
        recordSample(command, classify(thrown, httpStatus), startNanos);
    }

    public Outcome classify(Throwable thrown, Integer httpStatus) {
        if (thrown instanceof RollbackTransactionNotApprovedException) {
            return Outcome.AWAITING_APPROVAL;
        }
        if (thrown instanceof AbstractIdempotentCommandException) {
            return Outcome.DUPLICATE;
        }
        final Integer status = httpStatus != null ? httpStatus : httpStatusOf(thrown);
        return status != null && status >= 400 && status <= 499 ? Outcome.CLIENT_ERROR : Outcome.SERVER_ERROR;
    }

    private Integer httpStatusOf(Throwable thrown) {
        if (!(thrown instanceof RuntimeException exception)) {
            return null;
        }
        try {
            return errorHandler.handle(exception).getStatusCode();
        } catch (RuntimeException e) {
            log.debug("Could not map a loan command failure to an HTTP status", e);
            return null;
        }
    }

    private void recordSample(LoanCommand command, Outcome outcome, long startNanos) {
        try {
            final long durationNanos = clock.monotonicTime() - startNanos;
            timer(command, outcome).record(durationNanos, TimeUnit.NANOSECONDS);
            counters[index(command, outcome)].increment();
        } catch (RuntimeException e) {
            // A metrics failure must never reach the caller of the command
            log.warn("Could not record loan command metrics for command {} with outcome {}", command, outcome, e);
        }
    }

    private Timer timer(LoanCommand command, Outcome outcome) {
        final int index = index(command, outcome);
        Timer timer = timers.get(index);
        if (timer == null) {
            // Registering the same name and tags twice returns the same timer, so a race here is harmless
            timer = Timer.builder(TIMER_NAME).description(TIMER_DESCRIPTION).tag(TAG_COMMAND, command.tagValue())
                    .tag(TAG_OUTCOME, outcome.tagValue()).register(registry);
            timers.set(index, timer);
        }
        return timer;
    }

    // The ordinals only index this class's own arrays, so reordering the constants is safe
    @SuppressWarnings("EnumOrdinal")
    private static int index(LoanCommand command, Outcome outcome) {
        return command.ordinal() * OUTCOMES.length + outcome.ordinal();
    }
}
