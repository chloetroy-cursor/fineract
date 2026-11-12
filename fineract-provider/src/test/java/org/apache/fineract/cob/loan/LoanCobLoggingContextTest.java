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
package org.apache.fineract.cob.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.fineract.cob.COBBusinessStepService;
import org.apache.fineract.cob.data.BusinessStepNameAndOrder;
import org.apache.fineract.cob.domain.LockingService;
import org.apache.fineract.cob.exceptions.LockedReadException;
import org.apache.fineract.cob.listener.ChunkProcessingLoanItemListener;
import org.apache.fineract.cob.listener.InlineCOBLoanItemListener;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.logging.LoggingContext;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.service.ProgressiveLoanModelProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanCobLoggingContextTest {

    private static final Long LOAN_ID = 42L;

    @Mock
    private COBBusinessStepService cobBusinessStepService;

    @Mock
    private ProgressiveLoanModelProcessingService progressiveLoanModelProcessingService;

    @Mock
    private LockingService lockingService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private Loan loan;

    private final List<Map<String, String>> mdcSeenByTheBusinessSteps = new ArrayList<>();
    private final List<String> loanIdSeenByTheListener = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.reset();
        MDC.clear();
        given(loan.getId()).willReturn(LOAN_ID);
        given(cobBusinessStepService.run(any(), eq(loan))).willAnswer(invocation -> {
            mdcSeenByTheBusinessSteps.add(MDC.getCopyOfContextMap());
            return loan;
        });
        willAnswer(invocation -> loanIdSeenByTheListener.add(MDC.get(LoggingContext.LOAN_ID))).given(transactionTemplate)
                .executeWithoutResult(any());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MDC.clear();
    }

    @Test
    void theBusinessStepsOfALoanLogItsIdAndItIsGoneWhenTheLoanIsDone() throws Exception {
        final LoanItemProcessor processor = loanItemProcessor();

        processor.process(loan);

        assertThat(mdcSeenByTheBusinessSteps).hasSize(1);
        assertThat(mdcSeenByTheBusinessSteps.get(0)).containsEntry(LoggingContext.LOAN_ID, "42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void theLoanIdIsRemovedWhenABusinessStepFails() {
        final LoanItemProcessor processor = loanItemProcessor();
        given(cobBusinessStepService.run(any(), eq(loan))).willAnswer(invocation -> {
            mdcSeenByTheBusinessSteps.add(MDC.getCopyOfContextMap());
            throw new IllegalStateException("business step failed");
        });

        assertThatThrownBy(() -> processor.process(loan)).isInstanceOf(IllegalStateException.class);

        assertThat(mdcSeenByTheBusinessSteps.get(0)).containsEntry(LoggingContext.LOAN_ID, "42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void theModelRebuildOfAProgressiveLoanLogsTheLoanId() throws Exception {
        final LoanItemProcessor processor = loanItemProcessor();
        final List<String> loanIdSeenByTheRebuild = new ArrayList<>();
        given(loan.isProgressiveSchedule()).willReturn(true);
        given(progressiveLoanModelProcessingService.hasValidModel(eq(LOAN_ID), any())).willReturn(false);
        willAnswer(invocation -> loanIdSeenByTheRebuild.add(MDC.get(LoggingContext.LOAN_ID))).given(progressiveLoanModelProcessingService)
                .recalculateModelAndSave(loan);

        processor.process(loan);

        assertThat(loanIdSeenByTheRebuild).containsExactly("42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void inlineCobKeepsTheLoanIdOfTheSurroundingContextAfterTheLoanIsDone() throws Exception {
        final InlineCOBLoanItemProcessor processor = new InlineCOBLoanItemProcessor(cobBusinessStepService,
                progressiveLoanModelProcessingService);
        processor.setExecutionContext(executionContextWithBusinessSteps());
        // e.g. a loan command that runs inline COB for the loan before it continues
        final LoggingContext.Scope command = LoggingContext.withLoanId(LOAN_ID);

        processor.process(loan);

        assertThat(mdcSeenByTheBusinessSteps.get(0)).containsEntry(LoggingContext.LOAN_ID, "42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");
        command.close();
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void aBatchWorkerThreadLogsTenantAndLoanIdAndLeavesNothingBehindForItsNextTask() throws Exception {
        final LoanItemProcessor processor = loanItemProcessor();
        final FineractContext contextOfTheJob = new FineractContext("default",
                new FineractPlatformTenant(1L, "default", "Default", "UTC", null), null,
                new HashMap<>(Map.of(BusinessDateType.COB_DATE, LocalDate.of(2026, 1, 15))), ActionContext.COB);
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            // what InputChannelInterceptor does around a partition on a worker thread
            worker.submit(() -> {
                ThreadLocalContextUtil.init(contextOfTheJob);
                try {
                    return processor.process(loan);
                } finally {
                    ThreadLocalContextUtil.reset();
                }
            }).get(10, TimeUnit.SECONDS);
            final Map<String, String> nextTaskOnTheSameThread = worker.submit(MDC::getCopyOfContextMap).get(10, TimeUnit.SECONDS);

            assertThat(mdcSeenByTheBusinessSteps).hasSize(1);
            assertThat(mdcSeenByTheBusinessSteps.get(0)).containsEntry(LoggingContext.TENANT_ID, "default")
                    .containsEntry(LoggingContext.LOAN_ID, "42");
            assertThat(nextTaskOnTheSameThread == null ? Map.<String, String>of() : nextTaskOnTheSameThread)
                    .doesNotContainKeys(LoggingContext.TENANT_ID, LoggingContext.LOAN_ID);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void theListenersLogTheLoanIdOfTheLoanThatFailed() {
        final ChunkProcessingLoanItemListener chunkListener = new ChunkProcessingLoanItemListener(lockingService, transactionTemplate);
        final InlineCOBLoanItemListener inlineListener = new InlineCOBLoanItemListener(lockingService, transactionTemplate);

        chunkListener.onProcessError(loan, new IllegalStateException("processing failed"));
        inlineListener.onProcessError(loan, new IllegalStateException("processing failed"));
        chunkListener.onReadError(new LockedReadException(LOAN_ID, new IllegalStateException("locked")));

        assertThat(loanIdSeenByTheListener).containsExactly("42", "42", "42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void theLoanIdIsRemovedWhenAListenerFails() {
        final ChunkProcessingLoanItemListener listener = new ChunkProcessingLoanItemListener(lockingService, transactionTemplate);
        willAnswer(invocation -> {
            throw new IllegalStateException("lock update failed");
        }).given(transactionTemplate).executeWithoutResult(any());

        assertThatThrownBy(() -> listener.onProcessError(loan, new IllegalStateException("processing failed")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    private LoanItemProcessor loanItemProcessor() {
        final LoanItemProcessor processor = new LoanItemProcessor(cobBusinessStepService, progressiveLoanModelProcessingService);
        processor.setExecutionContext(executionContextWithBusinessSteps());
        return processor;
    }

    private static ExecutionContext executionContextWithBusinessSteps() {
        final ExecutionContext executionContext = new ExecutionContext();
        executionContext.put("businessSteps", Set.of(new BusinessStepNameAndOrder("APPLY_CHARGE_TO_OVERDUE_LOANS", 1L)));
        return executionContext;
    }
}
