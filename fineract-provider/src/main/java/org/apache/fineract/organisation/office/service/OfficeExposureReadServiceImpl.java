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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.office.data.CurrencyExposureData;
import org.apache.fineract.organisation.office.data.OfficeExposureData;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfficeExposureReadServiceImpl implements OfficeExposureReadService {

    private final OfficeRepositoryWrapper officeRepositoryWrapper;
    private final LoanRepository loanRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public OfficeExposureData retrieveExposure(final Long officeId) {
        final Office office = officeRepositoryWrapper.findOneWithNotFoundDetection(officeId);
        final List<Long> officeIds = jdbcTemplate.queryForList("SELECT o.id FROM m_office o WHERE o.hierarchy LIKE ?", Long.class,
                office.getHierarchy() + "%");

        final List<Loan> loans = loanRepository.findByClientOfficeIdsAndLoanStatus(officeIds, List.of(LoanStatus.ACTIVE));

        final Map<String, CurrencyTotals> totalsByCurrency = new TreeMap<>();
        for (final Loan loan : loans) {
            final MonetaryCurrency currency = loan.getCurrency();
            totalsByCurrency.computeIfAbsent(currency.getCode(), code -> new CurrencyTotals(currency))
                    .add(loan.getRepaymentScheduleInstallments());
        }

        final List<CurrencyExposureData> currencies = totalsByCurrency.values().stream().map(CurrencyTotals::toData).toList();
        return new OfficeExposureData(office.getId(), office.getName(), currencies);
    }

    private static final class CurrencyTotals {

        private final MonetaryCurrency currency;
        private long loanCount;
        private Money principal;
        private Money interest;
        private Money total;

        private CurrencyTotals(final MonetaryCurrency currency) {
            this.currency = currency;
            this.principal = Money.zero(currency);
            this.interest = Money.zero(currency);
            this.total = Money.zero(currency);
        }

        private void add(final List<LoanRepaymentScheduleInstallment> installments) {
            loanCount++;
            for (final LoanRepaymentScheduleInstallment installment : installments) {
                final Money installmentPrincipal = installment.getPrincipalOutstanding(currency);
                final Money installmentInterest = installment.getInterestOutstanding(currency);
                principal = principal.plus(installmentPrincipal);
                interest = interest.plus(installmentInterest);
                total = total.plus(installmentPrincipal).plus(installmentInterest).plus(installment.getFeeChargesOutstanding(currency))
                        .plus(installment.getPenaltyChargesOutstanding(currency));
            }
        }

        private CurrencyExposureData toData() {
            return new CurrencyExposureData(currency.getCode(), loanCount, principal.getAmount(), interest.getAmount(), total.getAmount());
        }
    }
}
