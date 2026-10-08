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
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepository;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.service.BackdatedTransactionValidationService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepository;
import org.apache.fineract.organisation.workingdays.domain.RepaymentRescheduleType;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.exception.LoanApplicationDateException;
import org.apache.fineract.portfolio.loanaccount.service.LoanBalanceService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoanTransactionValidatorImplTest {

    private static final String WEEKDAY_RECURRENCE = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR";

    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 10);

    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    private static final LocalDate HOLIDAY_START = LocalDate.of(2026, 10, 12);

    private static final LocalDate HOLIDAY_END = LocalDate.of(2026, 10, 14);

    private static final LocalDate DAY_AFTER_HOLIDAY = LocalDate.of(2026, 10, 15);

    private LoanTransactionValidatorImpl validator;

    private WorkingDays weekdays;

    private List<Holiday> holidays;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        validator = new LoanTransactionValidatorImpl(mock(FromJsonHelper.class), mock(LoanApplicationValidator.class),
                mock(LoanRepository.class), mock(LoanRepositoryWrapper.class), mock(ApplicationCurrencyRepository.class),
                mock(LoanUtilService.class), mock(EntityDatatableChecksWritePlatformService.class), mock(CalendarInstanceRepository.class),
                new LoanDownPaymentTransactionValidator(mock(LoanBalanceService.class)), mock(LoanDisbursementValidator.class),
                mock(CodeValueRepository.class), mock(ConfigurationDomainService.class), mock(BackdatedTransactionValidationService.class));
        weekdays = WorkingDays.builder().recurrence(WEEKDAY_RECURRENCE)
                .repaymentReschedulingType(RepaymentRescheduleType.SAME_DAY.getValue()).extendTermForDailyRepayments(false)
                .extendTermForRepaymentsOnHolidays(false).build();
        holidays = List.of(new Holiday().setName("Founders Day").setFromDate(HOLIDAY_START).setToDate(HOLIDAY_END));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-10-10", "2026-10-11" })
    void rejectsRepaymentOnWeekend(String isoDate) {
        LocalDate repaymentDate = LocalDate.parse(isoDate);

        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnNonWorkingDay(repaymentDate, weekdays, false));

        assertEquals("error.msg.loan.application.repayment.date.on.non.working.day", exception.getGlobalisationMessageCode());
        assertEquals("Repayment date cannot be on a non working day", exception.getDefaultUserMessage());
        assertEquals(repaymentDate, exception.getDefaultUserMessageArgs()[0]);
    }

    @Test
    void allowsWeekdayRepaymentWhenNonWorkingDaysAreBlocked() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnNonWorkingDay(HOLIDAY_START, weekdays, false));
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-10-10", "2026-10-11" })
    void allowsWeekendRepaymentWhenNonWorkingDaysArePermitted(String isoDate) {
        LocalDate repaymentDate = LocalDate.parse(isoDate);

        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnNonWorkingDay(repaymentDate, weekdays, true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-10-12", "2026-10-14" })
    void rejectsRepaymentOnHoliday(String isoDate) {
        LocalDate repaymentDate = LocalDate.parse(isoDate);

        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnHoliday(repaymentDate, false, holidays));

        assertEquals("error.msg.loan.application.repayment.date.on.holiday", exception.getGlobalisationMessageCode());
        assertEquals("Repayment date cannot be on a holiday", exception.getDefaultUserMessage());
        assertEquals(repaymentDate, exception.getDefaultUserMessageArgs()[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-10-11", "2026-10-15" })
    void allowsRepaymentOutsideHolidayRange(String isoDate) {
        LocalDate repaymentDate = LocalDate.parse(isoDate);

        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(repaymentDate, false, holidays));
    }

    @Test
    void allowsHolidayRepaymentWhenHolidaysArePermitted() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_START, true, holidays));
    }

    @Test
    void allowsRepaymentWhenNoHolidaysAreConfigured() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(SATURDAY, false, List.of()));
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(SUNDAY, false, List.of()));
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(DAY_AFTER_HOLIDAY, false, List.of()));
    }
}
