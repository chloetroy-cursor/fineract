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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.fineract.batch.exception.ErrorInfo;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.apache.fineract.commands.service.LoanCommandMetrics.LoanCommand;
import org.apache.fineract.commands.service.LoanCommandMetrics.Outcome;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.IdempotentCommandProcessFailedException;
import org.apache.fineract.infrastructure.core.exception.IdempotentCommandProcessSucceedException;
import org.apache.fineract.infrastructure.core.exception.IdempotentCommandProcessUnderProcessingException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exceptionmapper.DefaultExceptionMapper;
import org.apache.fineract.infrastructure.core.exceptionmapper.PlatformApiDataValidationExceptionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Pins the loan command meters to the "Metric naming and cardinality" chapter of the architecture documentation: the
 * names, the tag keys and values, how a failure is classified, which series exist at startup, and that nothing that
 * identifies a loan, a client or a tenant can become a tag.
 */
class LoanCommandMetricsTest {

    // Written out instead of read from the constants and enums of LoanCommandMetrics: dashboards and alerts depend on
    // these exact strings.
    private static final String TIMER = "fineract.loan.command.execution";
    private static final String COUNTER = "fineract.loan.commands";
    private static final List<String> COMMANDS = List.of("submit", "approve", "disburse", "repay");
    private static final List<String> OUTCOMES = List.of("success", "awaiting_approval", "duplicate", "client_error", "server_error");
    private static final Set<String> TAG_KEYS = Set.of("command", "outcome");

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final ErrorHandler mockedErrorHandler = mock(ErrorHandler.class);
    private GenericApplicationContext exceptionMappers;
    private LoanCommandMetrics underTest;

    @BeforeEach
    void setUp() {
        underTest = new LoanCommandMetrics(registry, mockedErrorHandler);
    }

    @AfterEach
    void tearDown() {
        if (exceptionMappers != null && exceptionMappers.isActive()) {
            exceptionMappers.close();
        }
    }

    @Test
    void meterNamesAndTagKeysAreTheDocumentedOnes() {
        assertEquals(TIMER, LoanCommandMetrics.TIMER_NAME);
        assertEquals(COUNTER, LoanCommandMetrics.COUNTER_NAME);
        assertEquals("command", LoanCommandMetrics.TAG_COMMAND);
        assertEquals("outcome", LoanCommandMetrics.TAG_OUTCOME);
        assertEquals(COMMANDS, Arrays.stream(LoanCommand.values()).map(LoanCommand::tagValue).toList());
        assertEquals(OUTCOMES, Arrays.stream(Outcome.values()).map(Outcome::tagValue).toList());
    }

    @Test
    void everyCounterSeriesExistsAtZeroAfterConstructionAndNoTimerDoes() {
        assertEquals(20, registry.getMeters().size(), "the registry must hold the 20 counters and nothing else");
        for (String command : COMMANDS) {
            for (String outcome : OUTCOMES) {
                Counter counter = registry.find(COUNTER).tag("command", command).tag("outcome", outcome).counter();
                assertNotNull(counter, "no counter for " + command + "/" + outcome);
                assertEquals(0.0, counter.count(), "counter for " + command + "/" + outcome);
                assertEquals(TAG_KEYS, tagKeys(counter));
            }
        }
        assertEquals(20, registry.find(COUNTER).counters().size());
        assertTrue(registry.find(TIMER).meters().isEmpty(), "a timer series must only appear once its combination is recorded");
    }

    @ParameterizedTest
    @CsvSource({ "LOAN, CREATE, SUBMIT", "LOAN, APPROVE, APPROVE", "LOAN, DISBURSE, DISBURSE", "LOAN, DISBURSETOSAVINGS, DISBURSE",
            "LOAN, DISBURSEWITHOUTAUTODOWNPAYMENT, DISBURSE", "LOAN, REPAYMENT, REPAY" })
    void recordedLoanCommandsMapToTheirCommandTag(String entityName, String actionName, LoanCommand expected) {
        assertEquals(expected, LoanCommand.of(CommandWrapper.wrap(actionName, entityName, 1L, null)));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = { "GLIMLOAN, CREATE", "WORKINGCAPITALLOAN, APPROVE", "LOAN, WAIVEINTERESTPORTION",
            "LOAN, UNDODISBURSAL", "LOANCHARGE, CREATE", "CLIENT, CREATE", "loan, create", "Loan, Repayment", "LOAN, NULL", "NULL, CREATE",
            "NULL, NULL", "LOAN, ''", "datatable_loan_1, CREATE" })
    void everyOtherCommandIsNotRecorded(String entityName, String actionName) {
        assertNull(LoanCommand.of(CommandWrapper.wrap(actionName, entityName, 1L, null)));
    }

    @Test
    void aMissingWrapperIsNotRecorded() {
        assertNull(LoanCommand.of(null));
    }

    @Test
    void theCommandBuildersOfTheLoanApiMapToTheDocumentedCommands() {
        assertEquals(LoanCommand.SUBMIT, LoanCommand.of(new CommandWrapperBuilder().createLoanApplication().build()));
        assertEquals(LoanCommand.APPROVE, LoanCommand.of(new CommandWrapperBuilder().approveLoanApplication(7L).build()));
        assertEquals(LoanCommand.DISBURSE, LoanCommand.of(new CommandWrapperBuilder().disburseLoanApplication(7L).build()));
        assertEquals(LoanCommand.DISBURSE, LoanCommand.of(new CommandWrapperBuilder().disburseLoanToSavingsApplication(7L).build()));
        assertEquals(LoanCommand.DISBURSE, LoanCommand.of(new CommandWrapperBuilder().disburseWithoutAutoDownPayment(7L).build()));
        assertEquals(LoanCommand.REPAY, LoanCommand.of(new CommandWrapperBuilder().loanRepaymentTransaction(7L).build()));
        assertNull(LoanCommand.of(new CommandWrapperBuilder().waiveInterestPortionTransaction(7L).build()));
        assertNull(LoanCommand.of(new CommandWrapperBuilder().createClient().build()));
    }

    @Test
    void aMakerCheckerRollbackIsAwaitingApprovalWhateverTheStatus() {
        RollbackTransactionNotApprovedException rollback = new RollbackTransactionNotApprovedException(1L, 2L);

        assertEquals(Outcome.AWAITING_APPROVAL, underTest.classify(rollback, null));
        assertEquals(Outcome.AWAITING_APPROVAL, underTest.classify(rollback, 200));
        assertEquals(Outcome.AWAITING_APPROVAL, underTest.classify(rollback, 500));
        verifyNoInteractions(mockedErrorHandler);
    }

    @ParameterizedTest
    @MethodSource("idempotentCommandExceptions")
    void anIdempotentReplayIsADuplicateWhateverTheStatus(RuntimeException duplicate) {
        assertEquals(Outcome.DUPLICATE, underTest.classify(duplicate, null));
        assertEquals(Outcome.DUPLICATE, underTest.classify(duplicate, 200));
        assertEquals(Outcome.DUPLICATE, underTest.classify(duplicate, 409));
        assertEquals(Outcome.DUPLICATE, underTest.classify(duplicate, 500));
        verifyNoInteractions(mockedErrorHandler);
    }

    private static Stream<Arguments> idempotentCommandExceptions() {
        CommandWrapper wrapper = CommandWrapper.wrap("REPAYMENT", "LOAN", 1L, null);
        CommandSource storedCommand = mock(CommandSource.class);
        return Stream.of(Arguments.of(new IdempotentCommandProcessUnderProcessingException(wrapper, "key")),
                Arguments.of(new IdempotentCommandProcessSucceedException(wrapper, "key", storedCommand)),
                Arguments.of(new IdempotentCommandProcessFailedException(wrapper, "key", storedCommand)));
    }

    @ParameterizedTest
    @CsvSource({ "400, CLIENT_ERROR", "403, CLIENT_ERROR", "404, CLIENT_ERROR", "409, CLIENT_ERROR", "499, CLIENT_ERROR",
            "500, SERVER_ERROR", "503, SERVER_ERROR", "599, SERVER_ERROR", "399, SERVER_ERROR", "200, SERVER_ERROR", "0, SERVER_ERROR" })
    void aKnownStatusDecidesBetweenClientAndServerErrorWithoutAskingTheErrorHandler(int httpStatus, Outcome expected) {
        assertEquals(expected, underTest.classify(new IllegalStateException("boom"), httpStatus));
        verifyNoInteractions(mockedErrorHandler);
    }

    @Test
    void withoutAStatusTheErrorHandlerIsAskedOnce() {
        IllegalStateException thrown = new IllegalStateException("boom");
        ErrorInfo forbidden = new ErrorInfo(403, 3001, "forbidden", null);
        when(mockedErrorHandler.handle(thrown)).thenReturn(forbidden);

        assertEquals(Outcome.CLIENT_ERROR, underTest.classify(thrown, null));

        verify(mockedErrorHandler, times(1)).handle(thrown);
    }

    @Test
    void withoutAStatusTheRealExceptionMappersDecide() {
        LoanCommandMetrics metrics = new LoanCommandMetrics(registry, realErrorHandler());

        assertEquals(Outcome.CLIENT_ERROR, metrics.classify(new PlatformApiDataValidationException(List.of()), null),
                "PlatformApiDataValidationExceptionMapper answers 400");
        assertEquals(Outcome.SERVER_ERROR, metrics.classify(new IllegalStateException("boom"), null),
                "an exception without a mapper falls to DefaultExceptionMapper, which answers 500");
    }

    @Test
    void anErrorIsAServerErrorAndNeverReachesTheErrorHandler() {
        assertEquals(Outcome.SERVER_ERROR, underTest.classify(new StackOverflowError(), null));
        assertEquals(Outcome.SERVER_ERROR, underTest.classify(new OutOfMemoryError(), null));
        verifyNoInteractions(mockedErrorHandler);
    }

    @Test
    void anErrorHandlerThatThrowsGivesServerError() {
        when(mockedErrorHandler.handle(any())).thenThrow(new IllegalStateException("no application context"));

        assertEquals(Outcome.SERVER_ERROR, underTest.classify(new IllegalArgumentException("boom"), null));
    }

    @Test
    void aSuccessIsOneTimerSampleWithItsDurationAndOneCounterIncrement() {
        long start = underTest.start();
        clock.add(120, TimeUnit.MILLISECONDS);
        underTest.recordSuccess(LoanCommand.REPAY, start);

        Timer timer = registry.find(TIMER).tag("command", "repay").tag("outcome", "success").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(120.0, timer.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(1.0, counter("repay", "success"));
        assertEquals(1, registry.find(TIMER).timers().size(), "no other timer may be registered");
        assertEquals(1.0, sumOfAllCounters(), "no other counter may move");
        assertEquals(20, registry.find(COUNTER).counters().size());
    }

    @Test
    void aFailureIsRecordedUnderItsClassifiedOutcomeWithItsDuration() {
        long start = underTest.start();
        clock.add(30, TimeUnit.MILLISECONDS);
        underTest.recordFailure(LoanCommand.DISBURSE, start, new IllegalStateException("boom"), 403);

        start = underTest.start();
        clock.add(7, TimeUnit.MILLISECONDS);
        underTest.recordFailure(LoanCommand.APPROVE, start, new RollbackTransactionNotApprovedException(1L, 2L), null);

        start = underTest.start();
        clock.add(900, TimeUnit.MILLISECONDS);
        underTest.recordFailure(LoanCommand.SUBMIT, start, new OutOfMemoryError(), null);

        assertEquals(30.0, timer("disburse", "client_error").totalTime(TimeUnit.MILLISECONDS));
        assertEquals(7.0, timer("approve", "awaiting_approval").totalTime(TimeUnit.MILLISECONDS));
        assertEquals(900.0, timer("submit", "server_error").totalTime(TimeUnit.MILLISECONDS));
        assertEquals(1.0, counter("disburse", "client_error"));
        assertEquals(1.0, counter("approve", "awaiting_approval"));
        assertEquals(1.0, counter("submit", "server_error"));
        assertEquals(3, registry.find(TIMER).timers().size());
        assertEquals(3.0, sumOfAllCounters());
        verifyNoInteractions(mockedErrorHandler);
    }

    @Test
    void theCounterEqualsTheTimerCountForEveryCombination() {
        int samples = 0;
        for (LoanCommand command : LoanCommand.values()) {
            for (Outcome outcome : Outcome.values()) {
                samples++;
                for (int i = 0; i < samples; i++) {
                    record(command, outcome);
                }
            }
        }

        assertEquals(20, registry.find(TIMER).timers().size());
        assertEquals(20, registry.find(COUNTER).counters().size());
        assertEquals(40, registry.getMeters().size(), "4 commands x 5 outcomes is the ceiling for both meters");
        int expected = 0;
        for (String command : COMMANDS) {
            for (String outcome : OUTCOMES) {
                expected++;
                assertEquals(expected, timer(command, outcome).count(), "timer " + command + "/" + outcome);
                assertEquals((double) expected, counter(command, outcome), "counter " + command + "/" + outcome);
            }
        }
    }

    @Test
    void everyMeterCarriesExactlyTheTagKeysCommandAndOutcome() {
        for (LoanCommand command : LoanCommand.values()) {
            for (Outcome outcome : Outcome.values()) {
                record(command, outcome);
            }
        }

        for (Meter meter : registry.getMeters()) {
            assertEquals(TAG_KEYS, tagKeys(meter), "tag keys of " + meter.getId());
            assertTrue(COMMANDS.contains(meter.getId().getTag("command")), "command value of " + meter.getId());
            assertTrue(OUTCOMES.contains(meter.getId().getTag("outcome")), "outcome value of " + meter.getId());
        }
    }

    @Test
    void commandsOnDifferentLoansAndClientsLandInOneSeries() {
        CommandWrapper repaymentOnLoan4711 = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).withClientId(8801L)
                .withJson("{\"transactionAmount\":1120}").build();
        CommandWrapper repaymentOnLoan9302 = new CommandWrapperBuilder().loanRepaymentTransaction(9302L).withClientId(8802L)
                .withJson("{\"transactionAmount\":560}").build();

        for (CommandWrapper wrapper : List.of(repaymentOnLoan4711, repaymentOnLoan9302)) {
            long start = underTest.start();
            clock.add(10, TimeUnit.MILLISECONDS);
            underTest.recordSuccess(LoanCommand.of(wrapper), start);
        }

        assertEquals(1, registry.find(TIMER).timers().size(), "two loans must not create two timer series");
        assertEquals(2, timer("repay", "success").count());
        assertEquals(2.0, counter("repay", "success"));
        assertEquals(21, registry.getMeters().size(), "20 counters and the one timer");
        for (Meter meter : registry.getMeters()) {
            assertEquals(TAG_KEYS, tagKeys(meter), "tag keys of " + meter.getId());
            for (Tag tag : meter.getId().getTags()) {
                for (String identifier : List.of("4711", "9302", "8801", "8802", "loans", "1120", "560")) {
                    assertTrue(!tag.getValue().contains(identifier) && !meter.getId().getName().contains(identifier),
                            meter.getId() + " carries " + identifier);
                }
            }
        }
    }

    @Test
    void aRegistryThatFailsDoesNotMakeRecordingThrow() {
        SimpleMeterRegistry refusingTimers = new SimpleMeterRegistry() {

            @Override
            protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
                throw new IllegalStateException("registry refuses timers");
            }
        };
        LoanCommandMetrics metrics = new LoanCommandMetrics(refusingTimers, mockedErrorHandler);

        assertDoesNotThrow(() -> metrics.recordSuccess(LoanCommand.REPAY, metrics.start()));
        assertDoesNotThrow(() -> metrics.recordFailure(LoanCommand.REPAY, metrics.start(), new IllegalStateException("boom"), 500));
        assertDoesNotThrow(() -> metrics.recordFailure(LoanCommand.REPAY, metrics.start(), new IllegalStateException("boom"), null));
    }

    @Test
    void aTimerThatFailsOnRecordDoesNotMakeRecordingThrow() {
        Timer failing = mock(Timer.class);
        doThrow(new IllegalStateException("record failed")).when(failing).record(anyLong(), any(TimeUnit.class));
        SimpleMeterRegistry failingOnRecord = new SimpleMeterRegistry() {

            @Override
            protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
                return failing;
            }
        };
        LoanCommandMetrics metrics = new LoanCommandMetrics(failingOnRecord, mockedErrorHandler);

        assertDoesNotThrow(() -> metrics.recordSuccess(LoanCommand.SUBMIT, metrics.start()));
        assertDoesNotThrow(() -> metrics.recordFailure(LoanCommand.SUBMIT, metrics.start(), new IllegalStateException("boom"), 400));
    }

    @Test
    void theSpringConstructorUsesTheMeterRegistryBeanWhenThereIsOne() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        SimpleMeterRegistry bean = new SimpleMeterRegistry();
        beans.registerSingleton("meterRegistry", bean);

        new LoanCommandMetrics(beans.getBeanProvider(MeterRegistry.class), mockedErrorHandler);

        assertEquals(20, bean.find(COUNTER).counters().size());
    }

    @Test
    void theSpringConstructorFallsBackToTheGlobalRegistryWithoutAMeterRegistryBean() {
        DefaultListableBeanFactory noMeterRegistry = new DefaultListableBeanFactory();
        try {
            LoanCommandMetrics metrics = new LoanCommandMetrics(noMeterRegistry.getBeanProvider(MeterRegistry.class), mockedErrorHandler);

            assertEquals(20, Metrics.globalRegistry.find(COUNTER).counters().size());
            assertDoesNotThrow(() -> metrics.recordSuccess(LoanCommand.APPROVE, metrics.start()));
        } finally {
            Metrics.globalRegistry.find(COUNTER).meters().forEach(Metrics.globalRegistry::remove);
            Metrics.globalRegistry.find(TIMER).meters().forEach(Metrics.globalRegistry::remove);
        }
    }

    private ErrorHandler realErrorHandler() {
        exceptionMappers = new GenericApplicationContext();
        exceptionMappers.registerBean(PlatformApiDataValidationExceptionMapper.class);
        exceptionMappers.refresh();
        return new ErrorHandler(exceptionMappers, new DefaultExceptionMapper());
    }

    private void record(LoanCommand command, Outcome outcome) {
        long start = underTest.start();
        switch (outcome) {
            case SUCCESS -> underTest.recordSuccess(command, start);
            case AWAITING_APPROVAL -> underTest.recordFailure(command, start, new RollbackTransactionNotApprovedException(1L, 2L), null);
            case DUPLICATE -> underTest.recordFailure(command, start,
                    new IdempotentCommandProcessUnderProcessingException(CommandWrapper.wrap("REPAYMENT", "LOAN", 1L, null), "key"), null);
            case CLIENT_ERROR -> underTest.recordFailure(command, start, new IllegalStateException("client"), 400);
            case SERVER_ERROR -> underTest.recordFailure(command, start, new IllegalStateException("server"), 500);
        }
    }

    private Timer timer(String command, String outcome) {
        return registry.get(TIMER).tag("command", command).tag("outcome", outcome).timer();
    }

    private double counter(String command, String outcome) {
        return registry.get(COUNTER).tag("command", command).tag("outcome", outcome).counter().count();
    }

    private double sumOfAllCounters() {
        return registry.find(COUNTER).counters().stream().mapToDouble(Counter::count).sum();
    }

    private static Set<String> tagKeys(Meter meter) {
        return meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
    }
}
