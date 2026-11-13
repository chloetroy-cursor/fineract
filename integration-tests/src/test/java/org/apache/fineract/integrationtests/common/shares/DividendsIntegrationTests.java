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
package org.apache.fineract.integrationtests.common.shares;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignShareDividendHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DividendsIntegrationTests {

    private final String[] dates = { "01 Jan 2015", "01 Apr 2015", "01 Oct 2015", "01 Dec 2015", "01 Mar 2016" };
    private final String[] shares = { "100", "200", "300", "100", "500" };
    private static final BigDecimal DIVIDEND_AMOUNT = new BigDecimal("50000");

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private FeignShareDividendHelper shareDividendHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.shareDividendHelper = new FeignShareDividendHelper(FineractFeignClientHelper.getFineractFeignClient());
    }

    @Test
    public void testCreateDividends() {
        final Integer productId = createShareProduct();
        ArrayList<Integer> shareAccounts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
            Assertions.assertNotNull(clientId);
            Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
            Assertions.assertNotNull(savingsAccountId);
            final Integer shareAccountId = createShareAccount(clientId, productId, savingsAccountId, dates[i], shares[i]);
            shareAccounts.add(shareAccountId);
            Assertions.assertNotNull(shareAccountId);
            Map<String, Object> shareAccountData = ShareAccountTransactionHelper.retrieveShareAccount(shareAccountId, requestSpec,
                    responseSpec);
            Assertions.assertNotNull(shareAccountData);
            // Approve share Account
            Map<String, Object> approveMap = new HashMap<>();
            approveMap.put("note", "Share Account Approval Note");
            approveMap.put("dateFormat", "dd MMMM yyyy");
            approveMap.put("approvedDate", "01 Jan 2016");
            approveMap.put("locale", "en");
            String approve = new Gson().toJson(approveMap);
            ShareAccountTransactionHelper.postCommand("approve", shareAccountId, approve, requestSpec, responseSpec);
            // Activate Share Account
            Map<String, Object> activateMap = new HashMap<>();
            activateMap.put("dateFormat", "dd MMMM yyyy");
            activateMap.put("activatedDate", "01 Jan 2016");
            activateMap.put("locale", "en");
            String activateJson = new Gson().toJson(activateMap);
            ShareAccountTransactionHelper.postCommand("activate", shareAccountId, activateJson, requestSpec, responseSpec);
        }

        final Long dividendId = shareDividendHelper.createDividend(productId.longValue(), "01 Jan 2015", "01 Apr 2016", DIVIDEND_AMOUNT,
                "dd MMMM yyyy", "en");

        assertProductDividend(productId, "shareAccountDividendStatusType.initiated");
        assertAccountDividends(productId, dividendId, shareAccounts);

        shareDividendHelper.approveDividend(productId.longValue(), dividendId);

        assertProductDividend(productId, "shareAccountDividendStatusType.approved");
        assertAccountDividends(productId, dividendId, shareAccounts);
    }

    private void assertProductDividend(final Integer productId, final String expectedStatusCode) {
        JsonNode productDividends = shareDividendHelper.getDividends(productId.longValue());
        Assertions.assertEquals(1, productDividends.get("totalFilteredRecords").asInt());
        JsonNode dividend = productDividends.get("pageItems").get(0);
        assertAmount(DIVIDEND_AMOUNT, dividend.get("amount"));
        Assertions.assertEquals(expectedStatusCode, dividend.get("status").get("code").asText());
        Assertions.assertEquals(LocalDate.of(2015, 1, 1), toLocalDate(dividend.get("dividendPeriodStartDate")));
        Assertions.assertEquals(LocalDate.of(2016, 4, 1), toLocalDate(dividend.get("dividendPeriodEndDate")));
    }

    /**
     * 50,000 spread over 201,400 share-days (100 x 456 + 200 x 366 + 300 x 183 + 100 x 122 + 500 x 31), then rounded to
     * the product's four decimals per account. Approving the payout leaves the per-account details in initiated state;
     * the scheduler job posts them.
     */
    private void assertAccountDividends(final Integer productId, final Long dividendId, final List<Integer> shareAccounts) {
        final BigDecimal[] expectedAmounts = { new BigDecimal("11320.7547"), new BigDecimal("18172.7905"), new BigDecimal("13629.5929"),
                new BigDecimal("3028.7984"), new BigDecimal("3848.0636") };
        JsonNode dividendDetails = shareDividendHelper.getDividendDetails(productId.longValue(), dividendId);
        Assertions.assertEquals(shareAccounts.size(), dividendDetails.get("totalFilteredRecords").asInt());
        int matched = 0;
        for (JsonNode dividendData : dividendDetails.get("pageItems")) {
            int accountId = dividendData.get("accountData").get("id").asInt();
            int index = shareAccounts.indexOf(accountId);
            Assertions.assertTrue(index >= 0, "unexpected share account " + accountId + " in dividend details");
            assertAmount(expectedAmounts[index], dividendData.get("amount"));
            Assertions.assertEquals("shareAccountDividendStatusType.initiated", dividendData.get("status").get("code").asText());
            matched++;
        }
        Assertions.assertEquals(shareAccounts.size(), matched);
    }

    private static void assertAmount(final BigDecimal expected, final JsonNode actual) {
        Assertions.assertEquals(0, expected.compareTo(actual.decimalValue()), () -> "expected " + expected + " but was " + actual);
    }

    private static LocalDate toLocalDate(final JsonNode dateArray) {
        return LocalDate.of(dateArray.get(0).asInt(), dateArray.get(1).asInt(), dateArray.get(2).asInt());
    }

    private Integer createShareProduct() {
        String shareProductJson = new ShareProductHelper().build();
        return ShareProductTransactionHelper.createShareProduct(shareProductJson, requestSpec, responseSpec);
    }

    private Integer createShareAccount(final Integer clientId, final Integer productId, final Integer savingsAccountId,
            String applicationDate, String requestedShares) {
        String josn = new ShareAccountHelper().withClientId(String.valueOf(clientId)).withProductId(String.valueOf(productId))
                .withExternalId("External1").withSavingsAccountId(String.valueOf(savingsAccountId)).withSubmittedDate("01 Jan 2016")
                .withApplicationDate(applicationDate).withRequestedShares(requestedShares).build();
        return ShareAccountTransactionHelper.createShareAccount(josn, requestSpec, responseSpec);
    }
}
