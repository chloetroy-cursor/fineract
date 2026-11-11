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
package org.apache.fineract.organisation.office.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.data.CurrencyExposureData;
import org.apache.fineract.organisation.office.data.OfficeExposureData;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeExposureReadServiceImplTest {

    private static final MockedStatic<MoneyHelper> MONEY_HELPER = Mockito.mockStatic(MoneyHelper.class);
    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, 0);
    private static final MonetaryCurrency EUR = new MonetaryCurrency("EUR", 2, 0);

    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OfficeExposureReadServiceImpl underTest;

    @BeforeAll
    static void init() {
        MONEY_HELPER.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        MONEY_HELPER.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
    }

    @AfterAll
    static void destruct() {
        MONEY_HELPER.close();
    }

    @Test
    void sumsOutstandingBalancesOfActiveLoansPerCurrencyAcrossChildOffices() {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        when(office.getName()).thenReturn("Head Office");
        when(office.getHierarchy()).thenReturn(".");
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(1L)).thenReturn(office);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(".%"))).thenReturn(List.of(1L, 2L));

        final Loan usdLoan1 = loan(USD, installment(USD, "1000", "120", "0", "0"), installment(USD, "1000", "120", "15", "5"));
        final Loan usdLoan2 = loan(USD, installment(USD, "500", "60", "0", "0"));
        final Loan eurLoan = loan(EUR, installment(EUR, "2000", "200", "0", "0"));
        when(loanRepository.findByClientOfficeIdsAndLoanStatus(List.of(1L, 2L), List.of(LoanStatus.ACTIVE)))
                .thenReturn(List.of(usdLoan1, eurLoan, usdLoan2));

        final OfficeExposureData result = underTest.retrieveExposure(1L);

        assertThat(result.getOfficeId()).isEqualTo(1L);
        assertThat(result.getOfficeName()).isEqualTo("Head Office");
        assertThat(result.getCurrencies()).extracting(CurrencyExposureData::getCurrencyCode).containsExactly("EUR", "USD");

        final CurrencyExposureData eur = result.getCurrencies().get(0);
        assertThat(eur.getActiveLoanCount()).isEqualTo(1);
        assertThat(eur.getPrincipalOutstanding()).isEqualByComparingTo("2000");
        assertThat(eur.getInterestOutstanding()).isEqualByComparingTo("200");
        assertThat(eur.getTotalOutstanding()).isEqualByComparingTo("2200");

        final CurrencyExposureData usd = result.getCurrencies().get(1);
        assertThat(usd.getActiveLoanCount()).isEqualTo(2);
        assertThat(usd.getPrincipalOutstanding()).isEqualByComparingTo("2500");
        assertThat(usd.getInterestOutstanding()).isEqualByComparingTo("300");
        assertThat(usd.getTotalOutstanding()).isEqualByComparingTo("2820");
    }

    @Test
    void returnsNoCurrenciesWhenOfficeHasNoActiveLoans() {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(2L);
        when(office.getName()).thenReturn("Branch");
        when(office.getHierarchy()).thenReturn(".2.");
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(2L)).thenReturn(office);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(".2.%"))).thenReturn(List.of(2L));
        when(loanRepository.findByClientOfficeIdsAndLoanStatus(List.of(2L), List.of(LoanStatus.ACTIVE))).thenReturn(List.of());

        final OfficeExposureData result = underTest.retrieveExposure(2L);

        assertThat(result.getCurrencies()).isEmpty();
    }

    private static Loan loan(final MonetaryCurrency currency, final LoanRepaymentScheduleInstallment... installments) {
        final Loan loan = mock(Loan.class);
        when(loan.getCurrency()).thenReturn(currency);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installments));
        return loan;
    }

    private static LoanRepaymentScheduleInstallment installment(final MonetaryCurrency currency, final String principal,
            final String interest, final String fees, final String penalties) {
        final Money principalOutstanding = Money.of(currency, new BigDecimal(principal));
        final Money interestOutstanding = Money.of(currency, new BigDecimal(interest));
        final Money feesOutstanding = Money.of(currency, new BigDecimal(fees));
        final Money penaltiesOutstanding = Money.of(currency, new BigDecimal(penalties));
        final LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getPrincipalOutstanding(currency)).thenReturn(principalOutstanding);
        when(installment.getInterestOutstanding(currency)).thenReturn(interestOutstanding);
        when(installment.getFeeChargesOutstanding(currency)).thenReturn(feesOutstanding);
        when(installment.getPenaltyChargesOutstanding(currency)).thenReturn(penaltiesOutstanding);
        return installment;
    }
}
