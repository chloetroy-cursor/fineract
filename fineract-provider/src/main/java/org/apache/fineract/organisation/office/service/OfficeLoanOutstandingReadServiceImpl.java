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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.fineract.organisation.office.data.OfficeLoanOutstandingCurrencyData;
import org.apache.fineract.organisation.office.data.OfficeLoanOutstandingData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OfficeLoanOutstandingReadServiceImpl implements OfficeLoanOutstandingReadService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PlatformSecurityContext context;
    private final OfficeReadPlatformService officeReadPlatformService;

    @Override
    public OfficeLoanOutstandingData retrieveActiveLoanOutstanding(final Long officeId) {
        final OfficeData office = this.officeReadPlatformService.retrieveOffice(officeId);
        this.context.validateAccessRights(office.getHierarchy());

        final CurrencyOutstandingMapper mapper = new CurrencyOutstandingMapper(this.sqlGenerator);
        final String hierarchySearchString = office.getHierarchy() + "%";
        final List<OfficeLoanOutstandingCurrencyData> currencies = this.jdbcTemplate.query(buildSql(mapper), mapper,
                LoanStatus.ACTIVE.getValue(), hierarchySearchString);

        return new OfficeLoanOutstandingData(office.getId(), office.getName(), office.getHierarchy(), currencies);
    }

    private String buildSql(final CurrencyOutstandingMapper mapper) {
        return "select " + mapper.schema() + " where l.loan_status_id = ? and o.hierarchy like ?"
                + " group by l.currency_code, rc.name, rc.decimal_places, rc.currency_multiplesof, rc.display_symbol,"
                + " rc.internationalized_name_code" + " order by l.currency_code";
    }

    static final class CurrencyOutstandingMapper implements RowMapper<OfficeLoanOutstandingCurrencyData> {

        private final DatabaseSpecificSQLGenerator sqlGenerator;

        CurrencyOutstandingMapper(final DatabaseSpecificSQLGenerator sqlGenerator) {
            this.sqlGenerator = sqlGenerator;
        }

        String schema() {
            return " l.currency_code as currencyCode, rc.name as currencyName, rc.decimal_places as currencyDigits,"
                    + " rc.currency_multiplesof as inMultiplesOf, rc.display_symbol as currencyDisplaySymbol,"
                    + " rc.internationalized_name_code as currencyNameCode," + " count(l.id) as activeLoanCount,"
                    + " sum(coalesce(l.principal_outstanding_derived, 0)) as principalOutstanding,"
                    + " sum(coalesce(l.interest_outstanding_derived, 0)) as interestOutstanding,"
                    + " sum(coalesce(l.fee_charges_outstanding_derived, 0)) as feeChargesOutstanding,"
                    + " sum(coalesce(l.penalty_charges_outstanding_derived, 0)) as penaltyChargesOutstanding,"
                    + " sum(coalesce(l.total_outstanding_derived, 0)) as totalOutstanding" + " from m_loan l"
                    + " left join m_client c on c.id = l.client_id" + " left join m_group g on g.id = l.group_id"
                    + " join m_office o on o.id = coalesce(c.office_id, g.office_id)" + " join m_currency rc on rc."
                    + sqlGenerator.escape("code") + " = l.currency_code";
        }

        @Override
        public OfficeLoanOutstandingCurrencyData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");

            final CurrencyData currencyData = new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf,
                    currencyDisplaySymbol, currencyNameCode);

            final Long activeLoanCount = rs.getLong("activeLoanCount");
            final BigDecimal principalOutstanding = rs.getBigDecimal("principalOutstanding");
            final BigDecimal interestOutstanding = rs.getBigDecimal("interestOutstanding");
            final BigDecimal feeChargesOutstanding = rs.getBigDecimal("feeChargesOutstanding");
            final BigDecimal penaltyChargesOutstanding = rs.getBigDecimal("penaltyChargesOutstanding");
            final BigDecimal totalOutstanding = rs.getBigDecimal("totalOutstanding");

            return new OfficeLoanOutstandingCurrencyData(currencyData, activeLoanCount, principalOutstanding, interestOutstanding,
                    feeChargesOutstanding, penaltyChargesOutstanding, totalOutstanding);
        }
    }
}
