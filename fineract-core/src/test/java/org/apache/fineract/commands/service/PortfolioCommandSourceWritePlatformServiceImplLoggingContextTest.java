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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.logging.LoggingContext;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioCommandSourceWritePlatformServiceImplLoggingContextTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private CommandSourceRepository commandSourceRepository;

    @Mock
    private CommandProcessingService processAndLogCommandService;

    @Mock
    private SchedulerJobRunnerReadService schedulerJobRunnerReadService;

    @Mock
    private ConfigurationDomainService configurationService;

    @Mock
    private AppUser appUser;

    private final List<String> loanIdSeenByTheCommand = new ArrayList<>();

    private PortfolioCommandSourceWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        MDC.clear();
        given(appUser.getId()).willReturn(1L);
        given(context.authenticatedUser(any(CommandWrapper.class))).willReturn(appUser);
        underTest = new PortfolioCommandSourceWritePlatformServiceImpl(context, commandSourceRepository, new FromJsonHelper(),
                processAndLogCommandService, schedulerJobRunnerReadService, configurationService, List.of());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void aLoanCommandLogsTheLoanIdWhileItRunsAndNotAfterwards() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().loanRepaymentTransaction(42L).withJson("{}").build();
        givenTheCommandRecordsTheLoanId(wrapper);

        underTest.logCommandSource(wrapper);

        assertThat(loanIdSeenByTheCommand).containsExactly("42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void theLoanIdIsRemovedWhenTheCommandFails() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().loanRepaymentTransaction(42L).withJson("{}").build();
        given(processAndLogCommandService.executeCommand(eq(wrapper), any(), anyBoolean())).willAnswer(invocation -> {
            loanIdSeenByTheCommand.add(MDC.get(LoggingContext.LOAN_ID));
            throw new IllegalStateException("command failed");
        });

        assertThatThrownBy(() -> underTest.logCommandSource(wrapper)).isInstanceOf(IllegalStateException.class);

        assertThat(loanIdSeenByTheCommand).containsExactly("42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void aCommandRunInsideALoanRequestLeavesTheLoanIdOfTheRequestInPlace() {
        // set by LoanIdLoggingContextFilter for the request, removed when the request is over
        LoggingContext.setLoanId(42L);
        final CommandWrapper wrapper = new CommandWrapperBuilder().loanRepaymentTransaction(42L).withJson("{}").build();
        givenTheCommandRecordsTheLoanId(wrapper);

        underTest.logCommandSource(wrapper);

        assertThat(loanIdSeenByTheCommand).containsExactly("42");
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");
    }

    @Test
    void aCommandWithoutALoanLogsNoLoanId() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().createClient().withJson("{}").build();
        givenTheCommandRecordsTheLoanId(wrapper);

        underTest.logCommandSource(wrapper);

        assertThat(loanIdSeenByTheCommand).containsExactly((String) null);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void aWorkingCapitalLoanCommandLogsNoLoanId() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().repaymentWorkingCapitalLoanTransaction(42L).withJson("{}").build();
        assertThat(wrapper.getLoanId()).isEqualTo(42L);
        givenTheCommandRecordsTheLoanId(wrapper);

        underTest.logCommandSource(wrapper);

        assertThat(loanIdSeenByTheCommand).containsExactly((String) null);
        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    private void givenTheCommandRecordsTheLoanId(final CommandWrapper wrapper) {
        given(processAndLogCommandService.executeCommand(eq(wrapper), any(), anyBoolean())).willAnswer(invocation -> {
            loanIdSeenByTheCommand.add(MDC.get(LoggingContext.LOAN_ID));
            return CommandProcessingResult.empty();
        });
    }
}
