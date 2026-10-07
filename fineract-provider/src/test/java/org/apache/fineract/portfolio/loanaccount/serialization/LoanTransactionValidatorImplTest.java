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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.workingdays.domain.RepaymentRescheduleType;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.loanaccount.exception.LoanApplicationDateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanTransactionValidatorImplTest {

    private static final String WEEKDAYS = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR";

    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);

    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    private static final LocalDate HOLIDAY_START = LocalDate.of(2026, 10, 12);

    private static final LocalDate HOLIDAY_END = LocalDate.of(2026, 10, 13);

    private static final LocalDate DAY_BEFORE_HOLIDAY = LocalDate.of(2026, 10, 11);

    private static final LocalDate DAY_AFTER_HOLIDAY = LocalDate.of(2026, 10, 14);

    @InjectMocks
    private LoanTransactionValidatorImpl underTest;

    @BeforeEach
    void setTenant() {
        // Working-day recurrence is evaluated in the tenant zone.
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", ZoneId.systemDefault().getId(), null));
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void rejectsRepaymentOnHolidayWhenTransactionsAreNotAllowed() {
        List<Holiday> holidays = List.of(holiday("Founders Day", HOLIDAY_START, HOLIDAY_END));

        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> underTest.validateRepaymentDateIsOnHoliday(HOLIDAY_START, false, holidays));

        assertEquals("error.msg.loan.application.repayment.date.on.holiday", exception.getGlobalisationMessageCode());
        assertEquals("Repayment date cannot be on a holiday", exception.getDefaultUserMessage());
        assertEquals(HOLIDAY_START, exception.getDefaultUserMessageArgs()[0]);
    }

    @Test
    void rejectsRepaymentOnFirstAndLastDayOfHoliday() {
        List<Holiday> holidays = List.of(holiday("Founders Day", HOLIDAY_START, HOLIDAY_END));

        assertThrows(LoanApplicationDateException.class, () -> underTest.validateRepaymentDateIsOnHoliday(HOLIDAY_START, false, holidays));
        assertThrows(LoanApplicationDateException.class, () -> underTest.validateRepaymentDateIsOnHoliday(HOLIDAY_END, false, holidays));
    }

    @Test
    void allowsRepaymentOnHolidayWhenTransactionsAreAllowed() {
        List<Holiday> holidays = List.of(holiday("Founders Day", HOLIDAY_START, HOLIDAY_END));

        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnHoliday(HOLIDAY_START, true, holidays));
    }

    @Test
    void allowsRepaymentOutsideHolidayRange() {
        List<Holiday> holidays = List.of(holiday("Founders Day", HOLIDAY_START, HOLIDAY_END),
                holiday("Other", LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 25)));

        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnHoliday(DAY_BEFORE_HOLIDAY, false, holidays));
        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnHoliday(DAY_AFTER_HOLIDAY, false, holidays));
    }

    @Test
    void allowsRepaymentWhenThereAreNoHolidays() {
        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnHoliday(MONDAY, false, List.of()));
    }

    @Test
    void rejectsRepaymentOnSaturdayWhenNonWorkingDaysAreNotAllowed() {
        assertEquals(DayOfWeek.SATURDAY, SATURDAY.getDayOfWeek());
        WorkingDays workingDays = weekdays();

        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> underTest.validateRepaymentDateIsOnNonWorkingDay(SATURDAY, workingDays, false));

        assertEquals("error.msg.loan.application.repayment.date.on.non.working.day", exception.getGlobalisationMessageCode());
        assertEquals("Repayment date cannot be on a non working day", exception.getDefaultUserMessage());
        assertEquals(SATURDAY, exception.getDefaultUserMessageArgs()[0]);
    }

    @Test
    void rejectsRepaymentOnSundayWhenNonWorkingDaysAreNotAllowed() {
        assertEquals(DayOfWeek.SUNDAY, SUNDAY.getDayOfWeek());

        assertThrows(LoanApplicationDateException.class, () -> underTest.validateRepaymentDateIsOnNonWorkingDay(SUNDAY, weekdays(), false));
    }

    @Test
    void allowsRepaymentOnWeekendWhenNonWorkingDaysAreAllowed() {
        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnNonWorkingDay(SATURDAY, weekdays(), true));
        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnNonWorkingDay(SUNDAY, weekdays(), true));
    }

    @Test
    void allowsRepaymentOnWeekday() {
        assertEquals(DayOfWeek.MONDAY, MONDAY.getDayOfWeek());

        assertDoesNotThrow(() -> underTest.validateRepaymentDateIsOnNonWorkingDay(MONDAY, weekdays(), false));
    }

    private static WorkingDays weekdays() {
        return new WorkingDays(WEEKDAYS, RepaymentRescheduleType.SAME_DAY.getValue(), false, false);
    }

    private static Holiday holiday(String name, LocalDate from, LocalDate to) {
        return new Holiday().setName(name).setFromDate(from).setToDate(to);
    }
}
