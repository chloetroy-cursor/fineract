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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.data.CurrencyExposureData;
import org.apache.fineract.organisation.office.data.OfficeExposureData;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class OfficeExposureReadServiceImplTest {

    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OfficeExposureReadServiceImpl underTest;

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesActiveLoansOfTheOfficeAndItsChildOfficesInOneQuery() {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(2L);
        when(office.getName()).thenReturn("Branch");
        when(office.getHierarchy()).thenReturn(".2.");
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(2L)).thenReturn(office);
        final CurrencyExposureData usd = new CurrencyExposureData("USD", 2, new BigDecimal("24000"), new BigDecimal("2880"),
                new BigDecimal("26880"));
        when(jdbcTemplate.query(eq(OfficeExposureReadServiceImpl.EXPOSURE_SQL), any(RowMapper.class), eq(".2.%"), eq(300)))
                .thenReturn(List.of(usd));

        final OfficeExposureData result = underTest.retrieveExposure(2L);

        assertThat(result.getOfficeId()).isEqualTo(2L);
        assertThat(result.getOfficeName()).isEqualTo("Branch");
        assertThat(result.getCurrencies()).containsExactly(usd);
    }

    @Test
    void mapsOneRowPerCurrencyAtTheCurrencyScale() throws SQLException {
        final ResultSet rs = mock(ResultSet.class);
        when(rs.getString("currencyCode")).thenReturn("USD");
        when(rs.getInt("currencyDigits")).thenReturn(2);
        when(rs.getLong("activeLoanCount")).thenReturn(1000L);
        when(rs.getBigDecimal("principalOutstanding")).thenReturn(new BigDecimal("12000000.000000"));
        when(rs.getBigDecimal("interestOutstanding")).thenReturn(new BigDecimal("1440000.000000"));
        when(rs.getBigDecimal("totalOutstanding")).thenReturn(new BigDecimal("13440000.000000"));

        final CurrencyExposureData result;
        try (MockedStatic<MoneyHelper> moneyHelper = Mockito.mockStatic(MoneyHelper.class)) {
            moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
            result = OfficeExposureReadServiceImpl.mapCurrencyExposure(rs, 0);
        }

        assertThat(result.getCurrencyCode()).isEqualTo("USD");
        assertThat(result.getActiveLoanCount()).isEqualTo(1000L);
        assertThat(result.getPrincipalOutstanding()).isEqualTo(new BigDecimal("12000000.00"));
        assertThat(result.getInterestOutstanding()).isEqualTo(new BigDecimal("1440000.00"));
        assertThat(result.getTotalOutstanding()).isEqualTo(new BigDecimal("13440000.00"));
    }
}
