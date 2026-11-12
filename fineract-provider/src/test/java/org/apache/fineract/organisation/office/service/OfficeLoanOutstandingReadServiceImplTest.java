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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.fineract.organisation.office.data.OfficeLoanOutstandingCurrencyData;
import org.apache.fineract.organisation.office.data.OfficeLoanOutstandingData;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.apache.fineract.organisation.office.service.OfficeLoanOutstandingReadServiceImpl.CurrencyOutstandingMapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class OfficeLoanOutstandingReadServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private ResultSet resultSet;

    private OfficeLoanOutstandingReadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OfficeLoanOutstandingReadServiceImpl(jdbcTemplate, sqlGenerator, context, officeReadPlatformService);
    }

    @Test
    void scopesToSubtreeAndActiveStatus() {
        final OfficeData office = office(4L, "Branch A", ".1.4.");
        when(officeReadPlatformService.retrieveOffice(4L)).thenReturn(office);
        when(sqlGenerator.escape("code")).thenReturn("code");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(LoanStatus.ACTIVE.getValue()), eq(".1.4.%")))
                .thenReturn(Collections.emptyList());

        final OfficeLoanOutstandingData result = service.retrieveActiveLoanOutstanding(4L);

        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(LoanStatus.ACTIVE.getValue()), eq(".1.4.%"));
        final String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("o.hierarchy like ?"));
        assertTrue(sql.contains("coalesce(c.office_id, g.office_id)"));
        assertEquals(4L, result.getOfficeId());
        assertEquals("Branch A", result.getOfficeName());
        assertEquals(".1.4.", result.getHierarchy());
        verify(context).validateAccessRights(".1.4.");
    }

    @Test
    void mapsOneRowPerCurrency() throws SQLException {
        final CurrencyOutstandingMapper mapper = new CurrencyOutstandingMapper(sqlGenerator);

        stubCurrencyRow("USD", "US Dollar", "currency.USD", "$", 2, 1, 12L, "10500.00", "812.40", "0", "15.00", "11327.40");
        final OfficeLoanOutstandingCurrencyData usd = mapper.mapRow(resultSet, 0);
        assertEquals("USD", usd.getCurrency().getCode());
        assertEquals(12L, usd.getActiveLoanCount());
        assertEquals(0, new BigDecimal("10500.00").compareTo(usd.getPrincipalOutstanding()));
        assertEquals(0, new BigDecimal("812.40").compareTo(usd.getInterestOutstanding()));
        assertEquals(0, new BigDecimal("0").compareTo(usd.getFeeChargesOutstanding()));
        assertEquals(0, new BigDecimal("15.00").compareTo(usd.getPenaltyChargesOutstanding()));
        assertEquals(0, new BigDecimal("11327.40").compareTo(usd.getTotalOutstanding()));

        stubCurrencyRow("KES", "Kenyan Shilling", "currency.KES", "KSh", 2, 1, 3L, "2000.00", "100.00", "50.00", "0", "2150.00");
        final OfficeLoanOutstandingCurrencyData kes = mapper.mapRow(resultSet, 1);
        assertEquals("KES", kes.getCurrency().getCode());
        assertEquals(3L, kes.getActiveLoanCount());
        assertEquals(0, new BigDecimal("2000.00").compareTo(kes.getPrincipalOutstanding()));
        assertEquals(0, new BigDecimal("100.00").compareTo(kes.getInterestOutstanding()));
        assertEquals(0, new BigDecimal("50.00").compareTo(kes.getFeeChargesOutstanding()));
        assertEquals(0, new BigDecimal("0").compareTo(kes.getPenaltyChargesOutstanding()));
        assertEquals(0, new BigDecimal("2150.00").compareTo(kes.getTotalOutstanding()));
    }

    @Test
    void deniesOfficesOutsideUserHierarchy() {
        final OfficeData office = office(4L, "Branch A", ".1.4.");
        when(officeReadPlatformService.retrieveOffice(4L)).thenReturn(office);
        doThrow(new NoAuthorizationException("The user doesn't have enough permissions to access the resource.")).when(context)
                .validateAccessRights(".1.4.");

        assertThrows(NoAuthorizationException.class, () -> service.retrieveActiveLoanOutstanding(4L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void propagatesOfficeNotFound() {
        when(officeReadPlatformService.retrieveOffice(99L)).thenThrow(new OfficeNotFoundException(99L));

        assertThrows(OfficeNotFoundException.class, () -> service.retrieveActiveLoanOutstanding(99L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void returnsEmptyCurrenciesWhenNoActiveLoans() {
        final OfficeData office = office(4L, "Branch A", ".1.4.");
        when(officeReadPlatformService.retrieveOffice(4L)).thenReturn(office);
        when(sqlGenerator.escape("code")).thenReturn("code");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(LoanStatus.ACTIVE.getValue()), eq(".1.4.%"))).thenReturn(List.of());

        final OfficeLoanOutstandingData result = service.retrieveActiveLoanOutstanding(4L);

        assertNotNull(result.getCurrencies());
        assertTrue(result.getCurrencies().isEmpty());
    }

    private static OfficeData office(final Long id, final String name, final String hierarchy) {
        return new OfficeData(id, name, null, null, null, hierarchy, null, null, null);
    }

    private void stubCurrencyRow(final String code, final String name, final String nameCode, final String displaySymbol,
            final int decimalPlaces, final int inMultiplesOf, final long activeLoanCount, final String principal, final String interest,
            final String fees, final String penalties, final String total) throws SQLException {
        when(resultSet.getString("currencyCode")).thenReturn(code);
        when(resultSet.getString("currencyName")).thenReturn(name);
        when(resultSet.getString("currencyNameCode")).thenReturn(nameCode);
        when(resultSet.getString("currencyDisplaySymbol")).thenReturn(displaySymbol);
        when(resultSet.findColumn("currencyDigits")).thenReturn(1);
        when(resultSet.findColumn("inMultiplesOf")).thenReturn(2);
        when(resultSet.getInt(1)).thenReturn(decimalPlaces);
        when(resultSet.getInt(2)).thenReturn(inMultiplesOf);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getLong("activeLoanCount")).thenReturn(activeLoanCount);
        when(resultSet.getBigDecimal("principalOutstanding")).thenReturn(new BigDecimal(principal));
        when(resultSet.getBigDecimal("interestOutstanding")).thenReturn(new BigDecimal(interest));
        when(resultSet.getBigDecimal("feeChargesOutstanding")).thenReturn(new BigDecimal(fees));
        when(resultSet.getBigDecimal("penaltyChargesOutstanding")).thenReturn(new BigDecimal(penalties));
        when(resultSet.getBigDecimal("totalOutstanding")).thenReturn(new BigDecimal(total));
    }
}
