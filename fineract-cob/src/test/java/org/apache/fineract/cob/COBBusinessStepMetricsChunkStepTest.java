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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.fineract.cob.domain.BatchBusinessStepRepository;
import org.apache.fineract.cob.service.ReloaderService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.springbatch.StepRetryPolicyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs {@code fineract.cob.loans.processed} through a Spring Batch chunk step built the way the Loan COB worker step
 * is: fault tolerant, with the immediate retry policy, skipping on any exception, and with a processor that calls
 * {@link COBBusinessStepService#run} through its {@code @Transactional} proxy. Only the database is missing: the
 * transactions are those of {@link InMemoryTransactionManager}, the job repository keeps nothing and the writer only
 * remembers what it was given.
 *
 * Five loans are read in chunks of three, so the first chunk holds loans 1, 2 and 3 and the second holds 4 and 5.
 */
class COBBusinessStepMetricsChunkStepTest {

    private static final String TIMER = "fineract.cob.step.execution";
    private static final String COUNTER = "fineract.cob.loans.processed";
    private static final int CHUNK_SIZE = 3;
    private static final int ATTEMPTS_PER_ITEM = 3;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final ReloaderService reloaderService = mock(ReloaderService.class);
    private final InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();
    private final List<Long> writtenAndCommitted = new ArrayList<>();
    private final List<Long> writtenAndRolledBack = new ArrayList<>();
    private final List<Double> successesSeenByTheWriter = new ArrayList<>();

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void theLoansOfAChunkBecomeSuccessesWhenTheChunkCommits() throws Exception {
        StepExecution stepExecution = runCob(loan -> null, loan -> false);

        assertEquals(ExitStatus.COMPLETED, stepExecution.getExitStatus());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), writtenAndCommitted);
        assertEquals(List.of(0.0, 3.0), successesSeenByTheWriter, "a chunk is counted when it commits, after its write");
        assertEquals(5.0, counter("success"));
        assertEquals(0.0, counter("failure"));
        assertEquals(5, timer("success").count());
        assertNull(findTimer("failure"));
    }

    /**
     * The failed attempt marks the chunk transaction rollback-only through the proxy, and the retry that returns does
     * not take that back. The loans of the chunk are failures because their work was rolled back, although each chain
     * returned in the end.
     */
    @Test
    void aLoanThatSucceedsOnTheRetryStillRollsItsChunkBackSoEveryLoanOfThatChunkIsAFailure() throws Exception {
        AtomicInteger attemptsOfLoanTwo = new AtomicInteger();

        StepExecution stepExecution = runCob(
                loan -> loan.getId() == 2 && attemptsOfLoanTwo.incrementAndGet() == 1 ? new IllegalStateException("first attempt") : null,
                loan -> false);

        assertEquals(ExitStatus.COMPLETED, stepExecution.getExitStatus());
        assertEquals(2, attemptsOfLoanTwo.get());
        assertEquals(List.of(1L, 2L, 3L), writtenAndRolledBack);
        assertEquals(List.of(4L, 5L), writtenAndCommitted);
        assertEquals(2.0, counter("success"));
        assertEquals(3.0, counter("failure"), "one for each loan of the chunk that was rolled back, not one for each attempt");
        assertEquals(1, timer("failure").count(), "the timer has the failed attempt");
        assertEquals(5, timer("success").count());
    }

    @Test
    void aLoanThatUsesUpItsAttemptsIsSkippedAndItsChunkIsRolledBack() throws Exception {
        StepExecution stepExecution = runCob(loan -> loan.getId() == 2 ? new IllegalStateException("every attempt") : null, loan -> false);

        assertEquals(ExitStatus.COMPLETED, stepExecution.getExitStatus());
        assertEquals(1, stepExecution.getProcessSkipCount());
        assertEquals(List.of(1L, 3L), writtenAndRolledBack);
        assertEquals(List.of(4L, 5L), writtenAndCommitted);
        assertEquals(2.0, counter("success"));
        assertEquals(3.0, counter("failure"), "the skipped loan once, and the two loans rolled back with it");
        assertEquals(ATTEMPTS_PER_ITEM, timer("failure").count());
        assertEquals(4, timer("success").count());
    }

    /**
     * Pins a known gap, not a requirement. When the write of a chunk fails on every attempt, Spring Batch rolls the
     * chunk back and then writes its items again one by one, each in a transaction of its own, without calling the
     * processor. No business step runs in those transactions, so nothing is counted in them: a loan whose single write
     * commits stays the {@code failure} it became when its chunk was rolled back.
     *
     * If the counter is ever made to follow those writes, loans 1 and 3 become successes and this test has to change
     * with it.
     */
    @Test
    void loansWrittenOneByOneAfterAFailedChunkWriteStayCountedAsFailures() throws Exception {
        StepExecution stepExecution = runCob(loan -> null, loan -> loan.getId() == 2);

        assertEquals(ExitStatus.COMPLETED, stepExecution.getExitStatus());
        assertEquals(1, stepExecution.getWriteSkipCount());
        assertEquals(List.of(1L, 3L, 4L, 5L), writtenAndCommitted, "loans 1 and 3 were written again on their own and committed");
        assertEquals(List.of(), writtenAndRolledBack);
        assertEquals(5, timer("success").count(), "no business step ran a second time");
        assertEquals(3.0, counter("failure"), "the chunk of loans 1, 2 and 3 that was rolled back");
        assertEquals(2.0, counter("success"), "loans 4 and 5 only");
    }

    private StepExecution runCob(Function<TestLoan, RuntimeException> failureOfTheBusinessStep, Predicate<TestLoan> writeFails)
            throws Exception {
        COBBusinessStepService service = cobBusinessStepService(failureOfTheBusinessStep);
        TreeMap<Long, String> executionMap = new TreeMap<>();
        executionMap.put(1L, "aBean");
        List<TestLoan> loans = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            loans.add(new TestLoan(id));
        }
        ResourcelessJobRepository jobRepository = new ResourcelessJobRepository();
        JobInstance jobInstance = jobRepository.createJobInstance("LOAN_COB", new JobParameters());
        JobExecution jobExecution = jobRepository.createJobExecution(jobInstance, new JobParameters(), new ExecutionContext());
        StepExecution stepExecution = jobRepository.createStepExecution("Loan COB worker - Step", jobExecution);

        Step step = new StepBuilder("Loan COB worker - Step", jobRepository).<TestLoan, TestLoan>chunk(CHUNK_SIZE) //
                .reader(new ListItemReader<>(loans)) //
                .processor(loan -> service.run(executionMap, loan)) //
                .writer(chunk -> write(chunk, writeFails)) //
                .faultTolerant() //
                .retryPolicy(StepRetryPolicyFactory.immediateRetryPolicy(ATTEMPTS_PER_ITEM, Exception.class)) //
                .skip(Exception.class) //
                .skipLimit(CHUNK_SIZE + 1) //
                .transactionManager(transactionManager) //
                .build();
        step.execute(stepExecution);
        return stepExecution;
    }

    @SuppressWarnings("unchecked")
    private COBBusinessStepService cobBusinessStepService(Function<TestLoan, RuntimeException> failureOfTheBusinessStep) {
        COBBusinessStep<TestLoan> businessStep = mock(COBBusinessStep.class);
        when(businessStep.getEnumStyledName()).thenReturn("STEP_A");
        when(businessStep.execute(any())).thenAnswer(invocation -> {
            RuntimeException failure = failureOfTheBusinessStep.apply(invocation.getArgument(0));
            if (failure != null) {
                throw failure;
            }
            return invocation.getArgument(0);
        });
        when(applicationContext.getBean("aBean")).thenReturn(businessStep);
        when(reloaderService.reload(any())).thenAnswer(invocation -> invocation.getArgument(0));
        COBBusinessStepServiceImpl target = new COBBusinessStepServiceImpl(mock(BatchBusinessStepRepository.class), applicationContext,
                mock(ListableBeanFactory.class), mock(BusinessEventNotifierService.class), mock(ConfigurationDomainService.class),
                reloaderService, new COBBusinessStepMetrics(registry));
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory
                .addAdvice(new TransactionInterceptor((TransactionManager) transactionManager, new AnnotationTransactionAttributeSource()));
        return (COBBusinessStepService) proxyFactory.getProxy();
    }

    private void write(Chunk<? extends TestLoan> chunk, Predicate<TestLoan> writeFails) {
        if (chunk.getItems().stream().anyMatch(writeFails)) {
            throw new IllegalStateException("the write failed");
        }
        successesSeenByTheWriter.add(counter("success"));
        List<Long> written = chunk.getItems().stream().map(TestLoan::getId).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                (status == STATUS_COMMITTED ? writtenAndCommitted : writtenAndRolledBack).addAll(written);
            }
        });
    }

    private Timer timer(String outcome) {
        Timer timer = findTimer(outcome);
        assertNotNull(timer, "no " + TIMER + " timer for outcome " + outcome);
        return timer;
    }

    private Timer findTimer(String outcome) {
        return registry.find(TIMER).tag("step", "STEP_A").tag("outcome", outcome).timer();
    }

    private double counter(String outcome) {
        return registry.get(COUNTER).tag("outcome", outcome).counter().count();
    }

    private static final class TestLoan extends AbstractPersistableCustom<Long> {

        private static final long serialVersionUID = 1L;

        private TestLoan(Long id) {
            setId(id);
        }
    }
}
