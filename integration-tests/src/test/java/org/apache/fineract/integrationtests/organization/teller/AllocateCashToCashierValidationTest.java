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
package org.apache.fineract.integrationtests.organization.teller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.PostFinancialActivityAccountsRequest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignTellerHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.accounting.FinancialActivityAccountHelper;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the behaviour introduced for FINERACT-2724: a non-numeric {@code txnAmount} on the "allocate cash to cashier"
 * endpoint must be rejected with a field-specific "not a valid number" validation error (via
 * {@code HttpMessageNotReadableErrorController}) rather than the generic invalid-JSON error, and without requiring
 * {@code txnAmount} to be widened from {@code BigDecimal} to {@code String} on the API contract.
 */
public class AllocateCashToCashierValidationTest {

    private FeignTellerHelper tellerHelper;
    private Long tellerId;
    private Long cashierId;

    @BeforeAll
    public static void ensureCashierFinancialActivityAccountsExist() {
        Utils.initializeRESTAssured();

        final RequestSpecification requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        final ResponseSpecification responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        final AccountHelper accountHelper = new AccountHelper(requestSpec, responseSpec);
        final FinancialActivityAccountHelper financialActivityAccountHelper = new FinancialActivityAccountHelper(requestSpec);

        // Allocating cash to a cashier posts journal entries between these two financial-activity accounts; the
        // teller endpoint 404s if either mapping is missing, so tests must not rely on it being seeded already.
        ensureFinancialActivityAccountMapping(financialActivityAccountHelper, accountHelper,
                FinancialActivity.CASH_AT_MAINVAULT.getValue());
        ensureFinancialActivityAccountMapping(financialActivityAccountHelper, accountHelper, FinancialActivity.CASH_AT_TELLER.getValue());
    }

    private static void ensureFinancialActivityAccountMapping(final FinancialActivityAccountHelper financialActivityAccountHelper,
            final AccountHelper accountHelper, final Integer financialActivityId) {
        final boolean alreadyMapped = financialActivityAccountHelper.getAllFinancialActivityAccounts().stream()
                .anyMatch(mapping -> financialActivityId.equals(mapping.getFinancialActivityData().getId()));
        if (alreadyMapped) {
            return;
        }

        final Account assetAccount = accountHelper.createAssetAccount();
        financialActivityAccountHelper.createFinancialActivityAccount(new PostFinancialActivityAccountsRequest()
                .financialActivityId(financialActivityId.longValue()).glAccountId(assetAccount.getAccountID().longValue()));
    }

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();

        final RequestSpecification requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpecification.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        final ResponseSpecification responseSpecification = new ResponseSpecBuilder().expectStatusCode(200).build();

        tellerHelper = new FeignTellerHelper(FineractFeignClientHelper.getFineractFeignClient());
        final Long staffId = Long.valueOf(StaffHelper.createStaff(requestSpecification, responseSpecification));
        tellerId = tellerHelper.createTeller().getResourceId();
        cashierId = tellerHelper.createCashier(tellerId, staffId);
    }

    @Test
    public void allocateCashWithNonNumericAmountReturnsFieldSpecificValidationError() {
        final String json = """
                {"locale":"en","dateFormat":"dd MMMM yyyy","txnDate":"01 January 2023","currencyCode":"USD",
                 "txnAmount":"not-a-number","txnNote":"Allocate cash"}
                """;

        final CallFailedRuntimeException error = tellerHelper.allocateCashToCashierExpectingError(tellerId, cashierId, json);

        assertEquals(400, error.getStatus());
        assertEquals("validation.msg.invalid.decimal.format", error.getUserMessageGlobalisationCode());
        final JsonObject body = JsonParser.parseString(error.getResponseBody()).getAsJsonObject();
        assertEquals("txnAmount", body.get("parameterName").getAsString());
        assertEquals("not-a-number", body.get("value").getAsString());
    }

    @Test
    public void allocateCashWithValidNumericAmountIsNotRejectedAsInvalidNumber() {
        tellerHelper.allocateCashToCashier(tellerId, cashierId, FeignTellerHelper.allocateCashRequest(BigDecimal.valueOf(100)));
    }

    @Test
    public void allocateCashWithMalformedJsonStillReturnsGenericInvalidJsonError() {
        final String malformedJson = "{\"currencyCode\":\"USD\",\"txnAmount\":100,\"txnDate\":\"01 January 2023\"";

        final CallFailedRuntimeException error = tellerHelper.allocateCashToCashierExpectingError(tellerId, cashierId, malformedJson);

        assertEquals(400, error.getStatus());
        assertEquals("error.msg.invalid.json.data", error.getUserMessageGlobalisationCode());
    }

}
