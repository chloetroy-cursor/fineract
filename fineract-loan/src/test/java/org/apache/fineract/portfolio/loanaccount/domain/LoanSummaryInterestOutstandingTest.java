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
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LoanSummaryInterestOutstandingTest {

    private static final MathContext MATH_CONTEXT = new MathContext(19, RoundingMode.HALF_EVEN);
    private static final MockedStatic<MoneyHelper> MONEY_HELPER = mockStatic(MoneyHelper.class);

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
    void interestOutstandingSubtractsWaivedInterest() {
        final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 0);
        final LoanRepaymentScheduleInstallment installment = new LoanRepaymentScheduleInstallment(mock(Loan.class), 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), BigDecimal.valueOf(12000), BigDecimal.valueOf(1440), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, false, false);
        installment.setInterestWaived(BigDecimal.valueOf(120));

        final LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        summary.updateSummary(currency, Money.of(currency, BigDecimal.valueOf(12000)), List.of(installment), null, Money.zero(currency),
                Money.zero(currency));

        assertMoney("interest waived", "120.00", summary.getTotalInterestWaived());
        assertMoney("interest outstanding", "1320.00", summary.getTotalInterestOutstanding());
        assertMoney("total outstanding", "13320.00", summary.getTotalOutstanding());
    }

    private static void assertMoney(final String label, final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> label + " expected " + expected + " but was " + actual);
    }
}
