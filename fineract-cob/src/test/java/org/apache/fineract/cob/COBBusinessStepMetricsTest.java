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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.fineract.cob.domain.BatchBusinessStepRepository;
import org.apache.fineract.cob.exceptions.BusinessStepException;
import org.apache.fineract.cob.service.ReloaderService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * Pins the COB business step meters to the "Metric naming and cardinality" chapter of the architecture documentation,
 * through {@link COBBusinessStepServiceImpl#run}, the only place that records them.
 */
class COBBusinessStepMetricsTest {

    // Written out instead of read from the constants of COBBusinessStepMetrics: dashboards and alerts depend on these
    // exact strings.
    private static final String TIMER = "fineract.cob.step.execution";
    private static final String COUNTER = "fineract.cob.loans.processed";
    private static final long RELOAD_MILLIS = 7;

    private final MockClock clock = new MockClock();
    private final long clockStart = clock.monotonicTime();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final ReloaderService reloaderService = mock(ReloaderService.class);
    private final ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);
    private final BusinessEventNotifierService businessEventNotifierService = mock(BusinessEventNotifierService.class);
    private final COBBusinessStepServiceImpl service = serviceRecordingTo(registry);

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void meterNamesAndTagKeysAreTheDocumentedOnes() {
        assertEquals(TIMER, COBBusinessStepMetrics.STEP_EXECUTION_TIMER);
        assertEquals(COUNTER, COBBusinessStepMetrics.LOANS_PROCESSED_COUNTER);
        assertEquals("step", COBBusinessStepMetrics.TAG_STEP);
        assertEquals("outcome", COBBusinessStepMetrics.TAG_OUTCOME);
        assertEquals("success", COBBusinessStepMetrics.OUTCOME_SUCCESS);
        assertEquals("failure", COBBusinessStepMetrics.OUTCOME_FAILURE);
    }

    @Test
    void bothCounterSeriesExistAtZeroAfterConstructionAndNoTimerDoes() {
        assertEquals(2, registry.getMeters().size(), "the registry must hold the two counters and nothing else");
        for (String outcome : List.of("success", "failure")) {
            Counter counter = registry.find(COUNTER).tag("outcome", outcome).counter();
            assertNotNull(counter, "no counter for outcome " + outcome);
            assertEquals(0.0, counter.count());
            assertEquals(Set.of("outcome"), tagKeys(counter), "the counter has no step tag");
        }
        assertTrue(registry.find(TIMER).meters().isEmpty(), "a timer series must only appear once its step and outcome are recorded");
    }

    @Test
    void everyStepOfEveryItemIsOneSuccessSampleAndEveryItemIsCountedOnce() {
        step("aBean", "STEP_A", 10);
        step("bBean", "STEP_B", 20);
        step("cBean", "STEP_C", 30);
        AbstractPersistableCustom<Long> loan = item();

        AbstractPersistableCustom<Long> result = null;
        for (int i = 0; i < 5; i++) {
            result = service.run(steps("aBean", "bBean", "cBean"), loan);
        }

        assertSame(loan, result);
        for (String step : List.of("STEP_A", "STEP_B", "STEP_C")) {
            assertEquals(5, timer(step, "success").count(), step);
            assertNull(findTimer(step, "failure"), "a step that never failed must not have a failure series");
        }
        assertEquals(5.0, counter("success"));
        assertEquals(0.0, counter("failure"));
        assertEquals(5, registry.getMeters().size(), "two counters and three timers");
        for (Timer timer : registry.find(TIMER).timers()) {
            assertEquals(Set.of("step", "outcome"), tagKeys(timer), "tag keys of " + timer.getId());
        }
    }

    @Test
    void theTimerCoversTheStepOnlyAndNotTheReloadBeforeIt() {
        step("aBean", "STEP_A", 10);
        step("cBean", "STEP_C", 30);

        for (int i = 0; i < 5; i++) {
            service.run(steps("aBean", "cBean"), item());
        }

        assertEquals(50.0, timer("STEP_A", "success").totalTime(TimeUnit.MILLISECONDS));
        assertEquals(150.0, timer("STEP_C", "success").totalTime(TimeUnit.MILLISECONDS));
        assertEquals(30.0, timer("STEP_C", "success").max(TimeUnit.MILLISECONDS));
        // 5 items x 2 steps were reloaded on the same clock, and none of that time is in the timers
        assertEquals(TimeUnit.MILLISECONDS.toNanos(5 * (10 + 30) + 5 * 2 * RELOAD_MILLIS), clock.monotonicTime() - clockStart);
    }

    @Test
    void aFailingStepEndsTheChainAndItsExceptionReachesTheCallerUnchanged() {
        IllegalStateException thrownByStep = new IllegalStateException("boom");
        step("aBean", "STEP_A", 10);
        failingStep("bBean", "STEP_B", 20, thrownByStep);
        COBBusinessStep<AbstractPersistableCustom<Long>> neverReached = step("cBean", "STEP_C", 30);

        BusinessStepException actual = assertThrows(BusinessStepException.class,
                () -> service.run(steps("aBean", "bBean", "cBean"), item()));

        assertSame(thrownByStep, actual.getCause());
        assertEquals("Error happened during business step execution", actual.getMessage());
        assertEquals("boom", thrownByStep.getMessage());
        assertEquals(0, actual.getSuppressed().length);
        assertEquals(0, thrownByStep.getSuppressed().length);
        verify(neverReached, never()).execute(any());

        assertEquals(1, timer("STEP_A", "success").count());
        assertEquals(1, timer("STEP_B", "failure").count());
        assertEquals(20.0, timer("STEP_B", "failure").totalTime(TimeUnit.MILLISECONDS));
        assertNull(findTimer("STEP_B", "success"));
        assertNull(findTimer("STEP_C", "success"));
        assertNull(findTimer("STEP_C", "failure"));
        assertEquals(1.0, counter("failure"), "the item is counted once, at its first failing step");
        assertEquals(0.0, counter("success"));
    }

    @Test
    void aStepThatCannotBeLookedUpCountsTheItemAsFailedWithoutATimerSample() {
        NoSuchBeanDefinitionException thrownByLookup = new NoSuchBeanDefinitionException("missing");
        when(applicationContext.getBean("missing")).thenThrow(thrownByLookup);

        BusinessStepException actual = assertThrows(BusinessStepException.class, () -> service.run(steps("missing"), item()));

        assertSame(thrownByLookup, actual.getCause());
        assertTrue(registry.find(TIMER).meters().isEmpty());
        assertEquals(1.0, counter("failure"));
        assertEquals(0.0, counter("success"));
    }

    @Test
    void aReloadThatFailsCountsTheItemAsFailedWithoutATimerSample() {
        COBBusinessStep<AbstractPersistableCustom<Long>> neverReached = step("aBean", "STEP_A", 10);
        IllegalStateException thrownByReload = new IllegalStateException("stale");
        when(reloaderService.reload(any())).thenThrow(thrownByReload);

        BusinessStepException actual = assertThrows(BusinessStepException.class, () -> service.run(steps("aBean"), item()));

        assertSame(thrownByReload, actual.getCause());
        verify(neverReached, never()).execute(any());
        assertTrue(registry.find(TIMER).meters().isEmpty());
        assertEquals(1.0, counter("failure"));
    }

    @Test
    void anEmptyOrMissingExecutionMapIsRejectedBeforeAnythingIsCounted() {
        BusinessStepException empty = assertThrows(BusinessStepException.class, () -> service.run(new TreeMap<>(), item()));
        BusinessStepException missing = assertThrows(BusinessStepException.class, () -> service.run(null, item()));

        assertEquals("Execution map is empty! COB Business step execution skipped!", empty.getMessage());
        assertEquals("Execution map is empty! COB Business step execution skipped!", missing.getMessage());
        assertEquals(0.0, counter("success"));
        assertEquals(0.0, counter("failure"));
        assertEquals(2, registry.getMeters().size());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " ", "\t" })
    void aStepWithoutANameRunsButIsNotPassedThroughAsATagValue(String enumStyledName) {
        COBBusinessStep<AbstractPersistableCustom<Long>> unnamed = step("unnamedBean", enumStyledName, 5);
        AbstractPersistableCustom<Long> loan = item();

        assertSame(loan, service.run(steps("unnamedBean"), loan));

        verify(unnamed).execute(loan);
        assertTrue(registry.find(TIMER).meters().isEmpty());
        assertEquals(1.0, counter("success"));
    }

    @Test
    void aStepNameThatThrowsDoesNotChangeTheOutcomeOfTheStep() {
        COBBusinessStep<AbstractPersistableCustom<Long>> nameThrows = step("nameThrows", "UNUSED", 5);
        when(nameThrows.getEnumStyledName()).thenThrow(new UnsupportedOperationException("name"));
        AbstractPersistableCustom<Long> loan = item();

        assertSame(loan, service.run(steps("nameThrows"), loan));

        assertEquals(1.0, counter("success"));
        assertEquals(0.0, counter("failure"));
    }

    @Test
    void aRegistryThatFailsDoesNotChangeTheResultOrTheExceptionOfAStep() {
        SimpleMeterRegistry refusingTimers = new SimpleMeterRegistry() {

            @Override
            protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
                throw new IllegalStateException("registry refuses timers");
            }
        };
        COBBusinessStepServiceImpl failingMetrics = serviceRecordingTo(refusingTimers);
        IllegalArgumentException thrownByStep = new IllegalArgumentException("boom");
        step("aBean", "STEP_A", 1);
        failingStep("bBean", "STEP_B", 1, thrownByStep);
        AbstractPersistableCustom<Long> loan = item();

        assertSame(loan, failingMetrics.run(steps("aBean"), loan));
        BusinessStepException actual = assertThrows(BusinessStepException.class, () -> failingMetrics.run(steps("bBean"), item()));

        assertSame(thrownByStep, actual.getCause());
        assertEquals(0, thrownByStep.getSuppressed().length);
        assertEquals(0, actual.getSuppressed().length);
    }

    @Test
    void anErrorFromAStepPropagatesAsTheSameInstanceAndIsRecordedAsFailure() {
        StackOverflowError thrownByStep = new StackOverflowError("deep");
        COBBusinessStep<AbstractPersistableCustom<Long>> step = step("errBean", "STEP_ERR", 0);
        when(step.execute(any())).thenThrow(thrownByStep);

        StackOverflowError actual = assertThrows(StackOverflowError.class, () -> service.run(steps("errBean"), item()));

        assertSame(thrownByStep, actual);
        assertEquals(1, timer("STEP_ERR", "failure").count());
        assertEquals(1.0, counter("failure"));
        assertEquals(0.0, counter("success"));
    }

    @Test
    void theTimerOfAStepIsRegisteredOnceAndReused() {
        step("aBean", "STEP_A", 1);

        service.run(steps("aBean"), item());
        Timer first = timer("STEP_A", "success");
        service.run(steps("aBean"), item());

        assertSame(first, timer("STEP_A", "success"));
        assertEquals(2, first.count());
        assertEquals(1, registry.find(TIMER).timers().size());
    }

    @Test
    void theBulkEventPathRecordsTheSameMeters() {
        when(configurationDomainService.isCOBBulkEventEnabled()).thenReturn(true);
        when(businessEventNotifierService.withExternalEventRecording(any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        step("aBean", "STEP_A", 10);

        service.run(steps("aBean"), item());

        assertEquals(1, timer("STEP_A", "success").count());
        assertEquals(1.0, counter("success"));
    }

    @Test
    void theOutcomeOfAStepIsOneOfTheTwoDocumentedValues() {
        step("aBean", "STEP_A", 1);
        failingStep("bBean", "STEP_B", 1, new IllegalStateException("boom"));

        service.run(steps("aBean"), item());
        assertInstanceOf(BusinessStepException.class,
                assertThrows(RuntimeException.class, () -> service.run(steps("aBean", "bBean"), item())));

        Set<String> outcomes = registry.getMeters().stream().map(meter -> meter.getId().getTag("outcome")).collect(Collectors.toSet());
        assertEquals(Set.of("success", "failure"), outcomes);
    }

    /**
     * The cardinality guard: the step name is the only thing a step contributes to the meters. Two different loans of
     * two different tenants are two samples of the same series, and nothing that identifies them is a tag.
     */
    @Test
    void differentLoansAndTenantsLandInOneSeriesWithoutIdentifyingTags() {
        List<String> identifiers = List.of("4711", "9302", "8801", "8802", "acme-bank", "globex-bank", "aBean");
        step("aBean", "CHECK_LOAN_REPAYMENT_DUE", 10);

        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "acme-bank", "Acme Bank", "UTC", null));
        service.run(steps("aBean"), item(4711L));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(2L, "globex-bank", "Globex Bank", "UTC", null));
        service.run(steps("aBean"), item(9302L));

        assertEquals(1, registry.find(TIMER).timers().size(), "two loans must not create two timer series");
        assertEquals(2, timer("CHECK_LOAN_REPAYMENT_DUE", "success").count());
        assertEquals(2.0, counter("success"));
        assertEquals(3, registry.getMeters().size(), "two counters and the one timer");
        for (Meter meter : registry.getMeters()) {
            Set<String> expectedKeys = meter instanceof Timer ? Set.of("step", "outcome") : Set.of("outcome");
            assertEquals(expectedKeys, tagKeys(meter), "tag keys of " + meter.getId());
            for (String identifier : identifiers) {
                assertFalse(meter.getId().getName().contains(identifier), meter.getId() + " carries " + identifier);
                assertTrue(meter.getId().getTags().stream().noneMatch(tag -> tag.getValue().contains(identifier)),
                        meter.getId() + " carries " + identifier);
            }
        }
    }

    private COBBusinessStepServiceImpl serviceRecordingTo(MeterRegistry meterRegistry) {
        when(reloaderService.reload(any())).thenAnswer(invocation -> {
            clock.add(RELOAD_MILLIS, TimeUnit.MILLISECONDS);
            return invocation.getArgument(0);
        });
        return new COBBusinessStepServiceImpl(mock(BatchBusinessStepRepository.class), applicationContext, mock(ListableBeanFactory.class),
                businessEventNotifierService, configurationDomainService, reloaderService, new COBBusinessStepMetrics(meterRegistry));
    }

    private COBBusinessStep<AbstractPersistableCustom<Long>> step(String beanName, String enumStyledName, long millis) {
        return registerStep(beanName, enumStyledName, millis, null);
    }

    private COBBusinessStep<AbstractPersistableCustom<Long>> failingStep(String beanName, String enumStyledName, long millis,
            RuntimeException failure) {
        return registerStep(beanName, enumStyledName, millis, failure);
    }

    @SuppressWarnings("unchecked")
    private COBBusinessStep<AbstractPersistableCustom<Long>> registerStep(String beanName, String enumStyledName, long millis,
            RuntimeException failure) {
        COBBusinessStep<AbstractPersistableCustom<Long>> step = mock(COBBusinessStep.class);
        when(step.getEnumStyledName()).thenReturn(enumStyledName);
        when(step.execute(any())).thenAnswer(invocation -> {
            clock.add(millis, TimeUnit.MILLISECONDS);
            if (failure != null) {
                throw failure;
            }
            return invocation.getArgument(0);
        });
        when(applicationContext.getBean(beanName)).thenReturn(step);
        return step;
    }

    private static TreeMap<Long, String> steps(String... beanNames) {
        TreeMap<Long, String> executionMap = new TreeMap<>();
        long order = 1;
        for (String beanName : beanNames) {
            executionMap.put(order++, beanName);
        }
        return executionMap;
    }

    private static AbstractPersistableCustom<Long> item() {
        return item(1L);
    }

    @SuppressWarnings("unchecked")
    private static AbstractPersistableCustom<Long> item(Long id) {
        AbstractPersistableCustom<Long> item = mock(AbstractPersistableCustom.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private Timer timer(String step, String outcome) {
        Timer timer = findTimer(step, outcome);
        assertNotNull(timer, "no " + TIMER + " timer for " + step + "/" + outcome);
        return timer;
    }

    private Timer findTimer(String step, String outcome) {
        return registry.find(TIMER).tag("step", step).tag("outcome", outcome).timer();
    }

    private double counter(String outcome) {
        return registry.get(COUNTER).tag("outcome", outcome).counter().count();
    }

    private static Set<String> tagKeys(Meter meter) {
        return meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
    }
}
