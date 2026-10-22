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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LoanSummaryInterestWaiverTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("12000");
    private static final BigDecimal INSTALLMENT_PRINCIPAL = new BigDecimal("1000");
    private static final BigDecimal MONTHLY_INTEREST = new BigDecimal("120");
    private static final BigDecimal TOTAL_BEFORE_WAIVER = new BigDecimal("13440.00");
    private static final BigDecimal TOTAL_AFTER_WAIVER = new BigDecimal("13320.00");
    private static final int INSTALLMENTS = 12;
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 1, 1);
    private static final MockedStatic<MoneyHelper> MONEY_HELPER = mockStatic(MoneyHelper.class);
    private static final MathContext MATH_CONTEXT = new MathContext(19, RoundingMode.HALF_EVEN);
    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("USD", 2, null);

    @BeforeAll
    static void init() {
        MONEY_HELPER.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        MONEY_HELPER.when(MoneyHelper::getMathContext).thenReturn(MATH_CONTEXT);
    }

    @AfterAll
    static void tearDown() {
        MONEY_HELPER.close();
    }

    @Test
    void interestWaiverOfOneMonthReducesOutstandingBalance() {
        final Loan loan = mock(Loan.class);
        final List<LoanRepaymentScheduleInstallment> installments = flatMonthlySchedule(loan);
        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);

        summary.updateSummary(CURRENCY, Money.of(CURRENCY, PRINCIPAL), installments, null, Money.zero(CURRENCY), Money.zero(CURRENCY));
        assertEquals(TOTAL_BEFORE_WAIVER, summary.getTotalOutstanding());

        installments.get(0).waiveInterestComponent(DISBURSEMENT_DATE.plusMonths(1), Money.of(CURRENCY, MONTHLY_INTEREST));
        summary.updateSummary(CURRENCY, Money.of(CURRENCY, PRINCIPAL), installments, null, Money.zero(CURRENCY), Money.zero(CURRENCY));

        assertEquals(TOTAL_AFTER_WAIVER, summary.getTotalOutstanding());
    }

    private static List<LoanRepaymentScheduleInstallment> flatMonthlySchedule(final Loan loan) {
        final List<LoanRepaymentScheduleInstallment> installments = new ArrayList<>();
        for (int number = 1; number <= INSTALLMENTS; number++) {
            final LocalDate fromDate = DISBURSEMENT_DATE.plusMonths(number - 1);
            final LocalDate dueDate = DISBURSEMENT_DATE.plusMonths(number);
            installments.add(new LoanRepaymentScheduleInstallment(loan, number, fromDate, dueDate, INSTALLMENT_PRINCIPAL, MONTHLY_INTEREST,
                    BigDecimal.ZERO, BigDecimal.ZERO, false, null));
        }
        return installments;
    }
}
