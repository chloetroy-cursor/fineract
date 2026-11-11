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

import io.micrometer.core.annotation.Timed;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.data.CurrencyExposureData;
import org.apache.fineract.organisation.office.data.OfficeExposureData;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfficeExposureReadServiceImpl implements OfficeExposureReadService {

    static final String EXPOSURE_SQL = "SELECT l.currency_code AS currencyCode, l.currency_digits AS currencyDigits,"
            + " COUNT(l.id) AS activeLoanCount, SUM(l.principal_outstanding_derived) AS principalOutstanding,"
            + " SUM(l.interest_outstanding_derived) AS interestOutstanding, SUM(l.total_outstanding_derived) AS totalOutstanding"
            + " FROM m_loan l JOIN m_client c ON c.id = l.client_id JOIN m_office o ON o.id = c.office_id"
            + " WHERE o.hierarchy LIKE ? AND l.loan_status_id = ? GROUP BY l.currency_code, l.currency_digits ORDER BY l.currency_code";

    private static final RowMapper<CurrencyExposureData> CURRENCY_EXPOSURE_MAPPER = OfficeExposureReadServiceImpl::mapCurrencyExposure;

    private final OfficeRepositoryWrapper officeRepositoryWrapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Timed(value = "fineract.office.exposure", description = "Time taken to aggregate an office's loan exposure", histogram = true)
    public OfficeExposureData retrieveExposure(final Long officeId) {
        final Office office = officeRepositoryWrapper.findOneWithNotFoundDetection(officeId);
        final List<CurrencyExposureData> currencies = jdbcTemplate.query(EXPOSURE_SQL, CURRENCY_EXPOSURE_MAPPER,
                office.getHierarchy() + "%", LoanStatus.ACTIVE.getValue());
        return new OfficeExposureData(office.getId(), office.getName(), currencies);
    }

    static CurrencyExposureData mapCurrencyExposure(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
        final int digits = rs.getInt("currencyDigits");
        return new CurrencyExposureData(rs.getString("currencyCode"), rs.getLong("activeLoanCount"),
                toCurrencyScale(rs.getBigDecimal("principalOutstanding"), digits),
                toCurrencyScale(rs.getBigDecimal("interestOutstanding"), digits),
                toCurrencyScale(rs.getBigDecimal("totalOutstanding"), digits));
    }

    private static BigDecimal toCurrencyScale(final BigDecimal amount, final int digits) {
        return amount.setScale(digits, MoneyHelper.getRoundingMode());
    }
}
