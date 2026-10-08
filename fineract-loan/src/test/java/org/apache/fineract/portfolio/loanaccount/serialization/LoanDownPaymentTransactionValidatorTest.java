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
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.loanaccount.exception.LoanApplicationDateException;
import org.apache.fineract.portfolio.loanaccount.service.LoanBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanDownPaymentTransactionValidatorTest {

    private static final String WEEKDAYS = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR";

    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 10);

    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    private static final LocalDate HOLIDAY_FROM = LocalDate.of(2026, 10, 12);

    private static final LocalDate HOLIDAY_TO = LocalDate.of(2026, 10, 14);

    private final LoanDownPaymentTransactionValidator validator = new LoanDownPaymentTransactionValidator(mock(LoanBalanceService.class));

    @BeforeEach
    void setTenant() {
        // Working-day recurrence reads the tenant timezone. Without it, weekday and Sunday checks NPE.
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void rejectsRepaymentOnSaturday() {
        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnNonWorkingDay(SATURDAY, weekdays(), false));

        assertEquals("error.msg.loan.application.repayment.date.on.non.working.day", exception.getGlobalisationMessageCode());
        assertEquals("Repayment date cannot be on a non working day", exception.getDefaultUserMessage());
        assertEquals(SATURDAY, exception.getDefaultUserMessageArgs()[0]);
    }

    @Test
    void rejectsRepaymentOnSunday() {
        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnNonWorkingDay(SUNDAY, weekdays(), false));

        assertEquals("error.msg.loan.application.repayment.date.on.non.working.day", exception.getGlobalisationMessageCode());
    }

    @Test
    void allowsRepaymentOnWeekday() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnNonWorkingDay(MONDAY, weekdays(), false));
    }

    @Test
    void allowsRepaymentOnNonWorkingDayWhenConfigured() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnNonWorkingDay(SATURDAY, weekdays(), true));
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnNonWorkingDay(SUNDAY, weekdays(), true));
    }

    @Test
    void rejectsRepaymentOnHoliday() {
        LocalDate holidayDate = LocalDate.of(2026, 10, 13);
        LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnHoliday(holidayDate, false, List.of(holiday())));

        assertEquals("error.msg.loan.application.repayment.date.on.holiday", exception.getGlobalisationMessageCode());
        assertEquals("Repayment date cannot be on a holiday", exception.getDefaultUserMessage());
        assertEquals(holidayDate, exception.getDefaultUserMessageArgs()[0]);
    }

    @Test
    void rejectsRepaymentOnHolidayRangeBoundaries() {
        assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_FROM, false, List.of(holiday())));
        assertThrows(LoanApplicationDateException.class,
                () -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_TO, false, List.of(holiday())));
    }

    @Test
    void allowsRepaymentOutsideHolidayRange() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_FROM.minusDays(1), false, List.of(holiday())));
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_TO.plusDays(1), false, List.of(holiday())));
    }

    @Test
    void allowsRepaymentOnHolidayWhenConfigured() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_FROM, true, List.of(holiday())));
    }

    @Test
    void allowsRepaymentWhenThereAreNoHolidays() {
        assertDoesNotThrow(() -> validator.validateRepaymentDateIsOnHoliday(HOLIDAY_FROM, false, List.of()));
    }

    private static WorkingDays weekdays() {
        return new WorkingDays(WEEKDAYS, 1, false, false);
    }

    private static Holiday holiday() {
        return new Holiday().setName("Office closure").setFromDate(HOLIDAY_FROM).setToDate(HOLIDAY_TO);
    }
}
