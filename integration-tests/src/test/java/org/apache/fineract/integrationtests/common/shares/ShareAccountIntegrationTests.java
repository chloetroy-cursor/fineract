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

import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.AccountChargesRequest;
import org.apache.fineract.client.models.AccountRequest;
import org.apache.fineract.client.models.GetAccountsCharges;
import org.apache.fineract.client.models.GetAccountsPurchasedShares;
import org.apache.fineract.client.models.GetAccountsTypeAccountIdResponse;
import org.apache.fineract.client.models.PutAccountsTypeAccountIdRequest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignShareAccountHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShareAccountIntegrationTests {

    private static final Logger LOG = LoggerFactory.getLogger(ShareAccountIntegrationTests.class);
    private static final String DATE_FORMAT = Utils.DATE_FORMAT;
    private static final String PURCHASED = "purchasedSharesType.purchased";
    private static final String REDEEMED = "purchasedSharesType.redeemed";
    private static final String CHARGE_PAYMENT = "charge.payment";
    private static final String APPLIED = "purchasedSharesStatusType.applied";
    private static final String APPROVED = "purchasedSharesStatusType.approved";
    private static final String REJECTED = "purchasedSharesStatusType.rejected";

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private ShareProductHelper shareProductHelper;
    private FeignShareAccountHelper shareAccountHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.requestSpec.header("Fineract-Platform-TenantId", "default");
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.shareAccountHelper = new FeignShareAccountHelper(FineractFeignClientHelper.getFineractFeignClient());
    }

    @Test
    public void testCreateShareProduct() {
        // This method will check create share product, get share product,
        // update share product.
        LOG.info("------------------------------CREATING NEW SHARE PRODUCT ---------------------------------------");
        shareProductHelper = new ShareProductHelper();
        final Integer shareProductId = createShareProduct();
        Assertions.assertNotNull(shareProductId);
        LOG.info("------------------------------CREATING SHARE PRODUCT COMPLETE---------------------------------------");

        LOG.info("------------------------------RETRIEVING SHARE PRODUCT---------------------------------------");
        Map<String, Object> shareProductData = ShareProductTransactionHelper.retrieveShareProduct(shareProductId, requestSpec,
                responseSpec);
        Assertions.assertNotNull(shareProductData);
        shareProductHelper.verifyShareProduct(shareProductData);

        LOG.info("------------------------------RETRIEVING SHARE PRODUCT COMPLETE---------------------------------------");

        LOG.info("------------------------------UPDATING SHARE PRODUCT---------------------------------------");

        Map<String, Object> shareProductDataForUpdate = new HashMap<>();

        shareProductDataForUpdate.put("totalShares", "2000");
        shareProductDataForUpdate.put("sharesIssued", "2000");

        String updateShareProductJsonString = new Gson().toJson(shareProductDataForUpdate);
        Integer updatedProductId = ShareProductTransactionHelper.updateShareProduct(shareProductId, updateShareProductJsonString,
                requestSpec, responseSpec);
        Assertions.assertNotNull(updatedProductId);
        Map<String, Object> updatedShareProductData = ShareProductTransactionHelper.retrieveShareProduct(updatedProductId, requestSpec,
                responseSpec);
        String updatedTotalShares = String.valueOf(updatedShareProductData.get("totalShares"));
        String updatedSharesIssued = String.valueOf(updatedShareProductData.get("totalSharesIssued"));
        Assertions.assertEquals("2000", updatedTotalShares);
        Assertions.assertEquals("2000", updatedSharesIssued);
        LOG.info("------------------------------UPDATING SHARE PRODUCT COMPLETE---------------------------------------");

    }

    @Test
    public void testCreateShareAccount() {
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);
        final Long shareAccountId = createShareAccount(clientId, productId, savingsAccountId);
        Assertions.assertNotNull(shareAccountId);
        Assertions.assertNotNull(shareAccountHelper.getShareAccount(shareAccountId));

        updateShareAccount(shareAccountId, 30, "02 March 2016", null);
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Set<GetAccountsPurchasedShares> transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(1, transactions.size());
        GetAccountsPurchasedShares transaction = transactions.iterator().next();
        Assertions.assertEquals(30, transaction.getNumberOfShares());
        assertAmount("60.0", transaction.getAmount());
        assertAmount("60.0", transaction.getAmountPaid());
        Assertions.assertEquals("02 March 2016", purchasedDate(transaction));
    }

    @Test
    public void testShareAccountApproval() {
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);
        final Long shareAccountId = createShareAccount(clientId, productId, savingsAccountId, createShareCharges());
        Assertions.assertNotNull(shareAccountId);
        Assertions.assertNotNull(shareAccountHelper.getShareAccount(shareAccountId));

        // Approve share Account
        shareAccountHelper.approve(shareAccountId, "01 January 2016", "Share Account Approval Note", DATE_FORMAT, "en");
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.approved", shareAccountData.getStatus().getCode());
        Assertions.assertEquals("01 January 2016", format(shareAccountData.getTimeline().getApprovedDate()));
        Set<GetAccountsPurchasedShares> transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(2, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            if (transactionType.equals(PURCHASED)) {
                Assertions.assertEquals(25, transaction.getNumberOfShares());
                assertAmount("52.0", transaction.getAmount());
                assertAmount("52.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals("01 January 2016", purchasedDate(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("0", transaction.getAmountPaid());
                Assertions.assertEquals(Utils.getLocalDateOfTenant(), transaction.getPurchasedDate());
            }
        }

        Assertions.assertEquals(25, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());
    }

    @Test
    public void rejectShareAccount() {
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);
        final Long shareAccountId = createShareAccount(clientId, productId, savingsAccountId, createShareCharges());
        Assertions.assertNotNull(shareAccountId);
        Assertions.assertNotNull(shareAccountHelper.getShareAccount(shareAccountId));

        // Reject share Account
        shareAccountHelper.reject(shareAccountId, "Share Account Rejection Note");
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.rejected", shareAccountData.getStatus().getCode());
        Assertions.assertEquals(Utils.getLocalDateOfTenant(), shareAccountData.getTimeline().getRejectedDate());

        Set<GetAccountsPurchasedShares> transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(2, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            if (transactionType.equals(PURCHASED)) {
                Assertions.assertEquals(25, transaction.getNumberOfShares());
                assertAmount("50.0", transaction.getAmount());
                assertAmount("50.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals("01 January 2016", purchasedDate(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("0", transaction.getAmountPaid());
                Assertions.assertEquals(Utils.getLocalDateOfTenant(), transaction.getPurchasedDate());
            }
        }

        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());
    }

    @Test
    public void testShareAccountUndoApproval() {
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);
        final Long shareAccountId = createShareAccount(clientId, productId, savingsAccountId, createShareCharges());
        Assertions.assertNotNull(shareAccountId);
        Assertions.assertNotNull(shareAccountHelper.getShareAccount(shareAccountId));

        // Approve share Account
        shareAccountHelper.approve(shareAccountId, "01 January 2016", "Share Account Approval Note", DATE_FORMAT, "en");
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.approved", shareAccountData.getStatus().getCode());
        Assertions.assertEquals("01 January 2016", format(shareAccountData.getTimeline().getApprovedDate()));

        // Undo Approval share Account
        shareAccountHelper.undoApproval(shareAccountId);
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.submitted.and.pending.approval", shareAccountData.getStatus().getCode());

        Set<GetAccountsPurchasedShares> transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(2, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            if (transactionType.equals(PURCHASED)) {
                Assertions.assertEquals(25, transaction.getNumberOfShares());
                assertAmount("52.0", transaction.getAmount());
                assertAmount("0.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals("01 January 2016", purchasedDate(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("0", transaction.getAmountPaid());
                Assertions.assertEquals(Utils.getLocalDateOfTenant(), transaction.getPurchasedDate());
            }
        }

        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(25, shareAccountData.getSummary().getTotalPendingForApprovalShares());
    }

    @Test
    public void testCreateShareAccountWithCharges() {
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);
        List<AccountChargesRequest> charges = createShareCharges();
        final Long shareAccountId = createShareAccount(clientId, productId, savingsAccountId, charges);
        Assertions.assertNotNull(shareAccountId);
        Assertions.assertNotNull(shareAccountHelper.getShareAccount(shareAccountId));

        updateShareAccount(shareAccountId, 30, "02 March 2016", charges);
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Set<GetAccountsPurchasedShares> transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(2, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            if (transactionType.equals(PURCHASED)) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("62.0", transaction.getAmount());
                assertAmount("60.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals("02 March 2016", purchasedDate(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("0", transaction.getAmountPaid());
                assertAmount("0", transaction.getChargeAmount());
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("2.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("2.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        // Approve share Account
        shareAccountHelper.approve(shareAccountId, "01 January 2016", "Share Account Approval Note", DATE_FORMAT, "en");
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.approved", shareAccountData.getStatus().getCode());
        Assertions.assertEquals("01 January 2016", format(shareAccountData.getTimeline().getApprovedDate()));

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("2.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        shareAccountHelper.activate(shareAccountId, "01 January 2016", DATE_FORMAT, "en");
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.active", shareAccountData.getStatus().getCode());
        Assertions.assertEquals("01 January 2016", format(shareAccountData.getTimeline().getActivatedDate()));

        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(2, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            if (transactionType.equals(PURCHASED)) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("62.0", transaction.getAmount());
                assertAmount("62.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals("02 March 2016", purchasedDate(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("2.0", transaction.getAmountPaid());
                assertAmount("0", transaction.getChargeAmount());
                Assertions.assertEquals("01 January 2016", purchasedDate(transaction));
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        Assertions.assertEquals(30, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());

        // apply additional shares
        applyAdditionalShares(shareAccountId, "01 April 2016", 15);
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(3, transactions.size());
        Long addtionalSharesRequestId = null;
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            String transactionDate = purchasedDate(transaction);
            if (transactionType.equals(PURCHASED) && transactionDate.equals("02 March 2016")) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("62.0", transaction.getAmount());
                assertAmount("62.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
            } else if (transactionType.equals(PURCHASED) && transactionDate.equals("01 April 2016")) {
                addtionalSharesRequestId = transaction.getId();
                Assertions.assertEquals(15, transaction.getNumberOfShares());
                assertAmount("32.0", transaction.getAmount());
                assertAmount("30.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals(APPLIED, statusCode(transaction));

            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("2.0", transaction.getAmountPaid());
                assertAmount("0", transaction.getChargeAmount());
                Assertions.assertEquals("01 January 2016", transactionDate);
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("4.0", chargeDef.getAmount());
                assertAmount("2.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        Assertions.assertEquals(30, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(15, shareAccountData.getSummary().getTotalPendingForApprovalShares());

        // Approve additional Shares request
        Assertions.assertNotNull(addtionalSharesRequestId);
        shareAccountHelper.approveAdditionalShares(shareAccountId, List.of(addtionalSharesRequestId));

        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(3, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            String transactionDate = purchasedDate(transaction);
            if (transactionType.equals(PURCHASED) && transactionDate.equals("02 March 2016")) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("62.0", transaction.getAmount());
                assertAmount("62.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
            } else if (transactionType.equals(PURCHASED) && transactionDate.equals("01 April 2016")) {
                Assertions.assertEquals(15, transaction.getNumberOfShares());
                assertAmount("32.0", transaction.getAmount());
                assertAmount("32.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals(APPROVED, statusCode(transaction));

            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("2.0", transaction.getAmountPaid());
                assertAmount("0", transaction.getChargeAmount());
                Assertions.assertEquals("01 January 2016", transactionDate);
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("4.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("4.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        Assertions.assertEquals(45, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());

        // apply aditional shres and reject it
        applyAdditionalShares(shareAccountId, "01 May 2016", 20);
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(4, transactions.size());
        addtionalSharesRequestId = null;
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            String transactionDate = purchasedDate(transaction);
            if (transactionType.equals(PURCHASED) && transactionDate.equals("01 May 2016")) {
                addtionalSharesRequestId = transaction.getId();
                Assertions.assertEquals(20, transaction.getNumberOfShares());
                assertAmount("42.0", transaction.getAmount());
                assertAmount("40.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals(APPLIED, statusCode(transaction));
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("6.0", chargeDef.getAmount());
                assertAmount("2.0", chargeDef.getAmountOutstanding());
                assertAmount("4.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        Assertions.assertEquals(45, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(20, shareAccountData.getSummary().getTotalPendingForApprovalShares());

        // rejectadditionalshares
        Assertions.assertNotNull(addtionalSharesRequestId);
        shareAccountHelper.rejectAdditionalShares(shareAccountId, List.of(addtionalSharesRequestId));
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(4, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            String transactionDate = purchasedDate(transaction);
            if (transactionType.equals(PURCHASED) && transactionDate.equals("01 May 2016")) {
                Assertions.assertEquals(20, transaction.getNumberOfShares());
                assertAmount("40.0", transaction.getAmount());
                assertAmount("40.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals(REJECTED, statusCode(transaction));
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("6.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("6.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("0.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }

        Assertions.assertEquals(45, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());

        // redeem shares
        shareAccountHelper.redeemShares(shareAccountId, 15, "05 May 2016", DATE_FORMAT, "en");
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(5, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            String transactionDate = purchasedDate(transaction);
            if (transactionType.equals(PURCHASED) && transactionDate.equals("02 March 2016")) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("62.0", transaction.getAmount());
                assertAmount("62.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
            } else if (transactionType.equals(PURCHASED) && transactionDate.equals("01 April 2016")) {
                Assertions.assertEquals(15, transaction.getNumberOfShares());
                assertAmount("32.0", transaction.getAmount());
                assertAmount("32.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals(APPROVED, statusCode(transaction));
            } else if (transactionType.equals(REDEEMED) && transactionDate.equals("05 May 2016")) {
                Assertions.assertEquals(15, transaction.getNumberOfShares());
                assertAmount("29.0", transaction.getAmount());
                assertAmount("29.0", transaction.getAmountPaid());
                assertAmount("1.0", transaction.getChargeAmount());
                Assertions.assertEquals(APPROVED, statusCode(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("2.0", transaction.getAmountPaid());
                assertAmount("0", transaction.getChargeAmount());
                Assertions.assertEquals("01 January 2016", transactionDate);
            }
        }

        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("6.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("6.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("1.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("1.0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }
        Assertions.assertEquals(30, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());

        // Close Share Account
        shareAccountHelper.close(shareAccountId, "10 May 2016", "Share Account Close Note", DATE_FORMAT, "en");
        shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.closed", shareAccountData.getStatus().getCode());
        transactions = shareAccountData.getPurchasedShares();
        Assertions.assertNotNull(transactions);
        Assertions.assertEquals(6, transactions.size());
        for (GetAccountsPurchasedShares transaction : transactions) {
            String transactionType = typeCode(transaction);
            String transactionDate = purchasedDate(transaction);
            if (transactionType.equals(PURCHASED) && transactionDate.equals("02 March 2016")) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("62.0", transaction.getAmount());
                assertAmount("62.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
            } else if (transactionType.equals(PURCHASED) && transactionDate.equals("01 April 2016")) {
                Assertions.assertEquals(15, transaction.getNumberOfShares());
                assertAmount("32.0", transaction.getAmount());
                assertAmount("32.0", transaction.getAmountPaid());
                assertAmount("2.0", transaction.getChargeAmount());
                assertAmount("2.0", transaction.getPurchasedPrice());
                Assertions.assertEquals(APPROVED, statusCode(transaction));
            } else if (transactionType.equals(REDEEMED) && transactionDate.equals("05 May 2016")) {
                Assertions.assertEquals(15, transaction.getNumberOfShares());
                assertAmount("29.0", transaction.getAmount());
                assertAmount("29.0", transaction.getAmountPaid());
                assertAmount("1.0", transaction.getChargeAmount());
                Assertions.assertEquals(APPROVED, statusCode(transaction));
            } else if (transactionType.equals(REDEEMED) && transactionDate.equals("10 May 2016")) {
                Assertions.assertEquals(30, transaction.getNumberOfShares());
                assertAmount("59.0", transaction.getAmount());
                assertAmount("59.0", transaction.getAmountPaid());
                assertAmount("1.0", transaction.getChargeAmount());
                Assertions.assertEquals(APPROVED, statusCode(transaction));
            } else if (transactionType.equals(CHARGE_PAYMENT)) {
                assertAmount("2.0", transaction.getAmount());
                assertAmount("2.0", transaction.getAmountPaid());
                assertAmount("0", transaction.getChargeAmount());
                Assertions.assertEquals("01 January 2016", transactionDate);
            }
        }
        // charges verification
        for (GetAccountsCharges chargeDef : shareAccountData.getCharges()) {
            String chargeTimeType = chargeTimeTypeCode(chargeDef);
            if (chargeTimeType.equals("chargeTimeType.activation")) {
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharespurchase")) {
                assertAmount("6.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("6.0", chargeDef.getAmountPaid());
            } else if (chargeTimeType.equals("chargeTimeType.sharesredeem")) {
                assertAmount("1.0", chargeDef.getAmountOrPercentage());
                assertAmount("2.0", chargeDef.getAmount());
                assertAmount("0.0", chargeDef.getAmountOutstanding());
                assertAmount("2.0", chargeDef.getAmountPaid());
            } else {
                Assertions.fail("Other Charge defintion found");
            }
        }
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalApprovedShares());
        Assertions.assertEquals(0, shareAccountData.getSummary().getTotalPendingForApprovalShares());
    }

    // Refactored Test 1
    @Test
    public void testChronologicalAdditionalSharesAfterRejectedTransaction() {
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);

        // Setup and activate share account with initial shares on 01 March 2016
        final Long shareAccountId = setupAndActivateShareAccount(clientId, productId, savingsAccountId, "01 March 2016");

        // Apply additional shares on 15 April 2016
        applyAdditionalShares(shareAccountId, "15 April 2016", 20);

        // Retrieve transactions and find the additional shares request
        Set<GetAccountsPurchasedShares> transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        Assertions.assertNotNull(transactions);

        // Find and reject the additional shares request (15 April 2016)
        Long additionalSharesRequestId = findTransactionId(transactions, PURCHASED, "15 April 2016");
        Assertions.assertNotNull(additionalSharesRequestId, "Additional shares request for 15 April 2016 should exist");

        // Reject the additional shares request
        shareAccountHelper.rejectAdditionalShares(shareAccountId, List.of(additionalSharesRequestId));

        // Verify transaction is rejected
        transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        verifyTransactionStatus(transactions, PURCHASED, "15 April 2016", REJECTED);

        // Now try to apply additional shares with a date BEFORE the rejected transaction (10 April 2016)
        // This should succeed because rejected transactions should be ignored in chronological validation
        applyAdditionalShares(shareAccountId, "10 April 2016", 15);

        // Verify the new transaction was successfully added
        transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        verifyTransactionWithShares(transactions, PURCHASED, "10 April 2016", 15, APPLIED);
    }

    @Test
    public void testChronologicalAccountClosureBeforeRejectedTransaction() {
        // FINERACT-2457: Account closure validation should ignore rejected/reversed transactions
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);

        // Setup and activate share account with initial shares on 01 March 2016
        final Long shareAccountId = setupAndActivateShareAccount(clientId, productId, savingsAccountId, "01 March 2016");

        // Apply additional shares on 20 May 2016
        applyAdditionalShares(shareAccountId, "20 May 2016", 30);

        // Retrieve transactions and find the additional shares request
        Set<GetAccountsPurchasedShares> transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        Assertions.assertNotNull(transactions);

        // Find and reject the additional shares request (20 May 2016)
        Long additionalSharesRequestId = findTransactionId(transactions, PURCHASED, "20 May 2016");
        Assertions.assertNotNull(additionalSharesRequestId, "Additional shares request for 20 May 2016 should exist");

        // Reject the additional shares request
        shareAccountHelper.rejectAdditionalShares(shareAccountId, List.of(additionalSharesRequestId));

        // Verify transaction is rejected
        transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        verifyTransactionStatus(transactions, PURCHASED, "20 May 2016", REJECTED);

        // Now try to close the account with a date BEFORE the rejected transaction (15 May 2016)
        // This should succeed because rejected transactions should be ignored in chronological validation
        shareAccountHelper.close(shareAccountId, "15 May 2016", "Share Account Close Note", DATE_FORMAT, "en");

        // Verify the account was successfully closed
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.closed", shareAccountData.getStatus().getCode());
        Assertions.assertEquals("15 May 2016", format(shareAccountData.getTimeline().getClosedDate()));
    }

    // Additional Test 1: Verify original validation still works (Negative Test)
    @Test
    public void testChronologicalAdditionalSharesBeforeActiveTransactionShouldFail() {
        // FINERACT-2457: Verify that the fix didn't break the original chronological validation
        // Transactions BEFORE active/approved transactions should still be REJECTED
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);

        // Setup and activate share account with initial shares on 01 March 2016
        final Long shareAccountId = setupAndActivateShareAccount(clientId, productId, savingsAccountId, "01 March 2016");

        // Apply additional shares on 15 April 2016 (this remains active/approved)
        applyAdditionalShares(shareAccountId, "15 April 2016", 20);

        // Verify the transaction exists and is in applied/active state
        Set<GetAccountsPurchasedShares> transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        Assertions.assertNotNull(transactions);

        Long transactionId = findTransactionId(transactions, PURCHASED, "15 April 2016");
        Assertions.assertNotNull(transactionId, "Transaction for 15 April 2016 should exist");

        // Try to apply additional shares BEFORE the active transaction (10 April 2016)
        // This should FAIL because the April 15 transaction is ACTIVE/APPROVED
        CallFailedRuntimeException error = Assertions.assertThrows(CallFailedRuntimeException.class,
                () -> applyAdditionalShares(shareAccountId, "10 April 2016", 15));
        Assertions.assertEquals(400, error.getStatus());
    }

    // Additional Test 3: Test edge case - transaction on same date as rejected transaction
    @Test
    public void testChronologicalAdditionalSharesOnSameDateAsRejectedTransaction() {
        // FINERACT-2457: Test behavior when applying shares on the SAME date as a rejected transaction
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);

        // Setup and activate share account with initial shares on 01 March 2016
        final Long shareAccountId = setupAndActivateShareAccount(clientId, productId, savingsAccountId, "01 March 2016");

        // Apply and reject shares on 15 April 2016
        applyAdditionalShares(shareAccountId, "15 April 2016", 20);
        Set<GetAccountsPurchasedShares> transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        Long txId = findTransactionId(transactions, PURCHASED, "15 April 2016");
        Assertions.assertNotNull(txId);
        shareAccountHelper.rejectAdditionalShares(shareAccountId, List.of(txId));

        // Verify transaction is rejected
        transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        verifyTransactionStatus(transactions, PURCHASED, "15 April 2016", REJECTED);

        // Try to apply shares on the SAME date as the rejected transaction
        // This should succeed since rejected transactions are ignored
        applyAdditionalShares(shareAccountId, "15 April 2016", 15);

        // Verify the new transaction was successfully added
        transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();

        // Count how many transactions exist for 15 April 2016
        int countForDate = 0;
        int appliedCountForDate = 0;

        for (GetAccountsPurchasedShares transaction : transactions) {
            if (typeCode(transaction).equals(PURCHASED) && purchasedDate(transaction).equals("15 April 2016")) {
                countForDate++;
                if (statusCode(transaction).equals(APPLIED)) {
                    appliedCountForDate++;
                    Assertions.assertEquals(15, transaction.getNumberOfShares());
                }
            }
        }

        // Should have 2 transactions for this date: 1 rejected, 1 applied
        Assertions.assertEquals(2, countForDate, "Should have 2 transactions for 15 April 2016");
        Assertions.assertEquals(1, appliedCountForDate, "Should have 1 applied transaction for 15 April 2016");
    }

    // Additional Test 4: Test account closure before active transaction should still fail
    @Test
    public void testChronologicalAccountClosureBeforeActiveTransactionShouldFail() {
        // FINERACT-2457: Verify that closing account before active transactions is still blocked
        shareProductHelper = new ShareProductHelper();
        final Integer productId = createShareProduct();
        Assertions.assertNotNull(productId);
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(clientId);
        Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        Assertions.assertNotNull(savingsAccountId);

        // Setup and activate share account with initial shares on 01 March 2016
        final Long shareAccountId = setupAndActivateShareAccount(clientId, productId, savingsAccountId, "01 March 2016");

        // Apply additional shares on 20 May 2016 (and keep it active/approved)
        applyAdditionalShares(shareAccountId, "20 May 2016", 30);

        // Verify the transaction exists and is active
        Set<GetAccountsPurchasedShares> transactions = shareAccountHelper.getShareAccount(shareAccountId).getPurchasedShares();
        Long transactionId = findTransactionId(transactions, PURCHASED, "20 May 2016");
        Assertions.assertNotNull(transactionId, "Transaction for 20 May 2016 should exist");

        // Try to close account on 15 May 2016 (before the active transaction)
        // This should FAIL
        CallFailedRuntimeException error = Assertions.assertThrows(CallFailedRuntimeException.class,
                () -> shareAccountHelper.close(shareAccountId, "15 May 2016", "Share Account Close Note", DATE_FORMAT, "en"));
        Assertions.assertEquals(400, error.getStatus());

        // Verify account is still active (not closed)
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.active", shareAccountData.getStatus().getCode(),
                "Account should still be active since closure was rejected");
    }

    private Integer createShareProduct() {
        String shareProductJson = shareProductHelper.build();
        return ShareProductTransactionHelper.createShareProduct(shareProductJson, requestSpec, responseSpec);
    }

    private Long createShareAccount(final Integer clientId, final Integer productId, final Integer savingsAccountId) {
        return createShareAccount(clientId, productId, savingsAccountId, null);
    }

    private Long createShareAccount(final Integer clientId, final Integer productId, final Integer savingsAccountId,
            List<AccountChargesRequest> charges) {
        AccountRequest request = new AccountRequest().clientId(clientId.longValue()).productId(productId.longValue())
                .externalId("External1").savingsAccountId(savingsAccountId.longValue()).submittedDate("01 January 2016")
                .applicationDate("01 January 2016").requestedShares(25L).dateFormat(DATE_FORMAT).locale("en_GB");
        if (charges != null) {
            request.charges(charges);
        }
        return shareAccountHelper.applyShareAccount(request);
    }

    /** One activation, one purchase and one redeem charge, as every charge-bearing scenario in this class uses. */
    private List<AccountChargesRequest> createShareCharges() {
        Integer activationChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getShareAccountActivationChargeJson());
        Integer purchaseChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getShareAccountPurchaseChargeJson());
        Integer redeemChargeId = ChargesHelper.createCharges(requestSpec, responseSpec, ChargesHelper.getShareAccountRedeemChargeJson());
        return List.of(createCharge(activationChargeId, "2"), createCharge(purchaseChargeId, "2"), createCharge(redeemChargeId, "1"));
    }

    private AccountChargesRequest createCharge(final Integer chargeId, String amount) {
        return new AccountChargesRequest().chargeId(chargeId.longValue()).amount(new BigDecimal(amount));
    }

    private void updateShareAccount(Long shareAccountId, int requestedShares, String applicationDate, List<AccountChargesRequest> charges) {
        PutAccountsTypeAccountIdRequest request = new PutAccountsTypeAccountIdRequest().requestedShares(requestedShares)
                .applicationDate(applicationDate).dateFormat(DATE_FORMAT).locale("en_GB");
        if (charges != null) {
            request.charges(charges);
        }
        shareAccountHelper.updateShareAccount(shareAccountId, request);
    }

    private void applyAdditionalShares(Long shareAccountId, String requestedDate, long requestedShares) {
        shareAccountHelper.applyAdditionalShares(shareAccountId, requestedShares, requestedDate, DATE_FORMAT, "en");
    }

    private Long findTransactionId(Set<GetAccountsPurchasedShares> transactions, String transactionTypeCode, String expectedDate) {
        for (GetAccountsPurchasedShares transaction : transactions) {
            if (typeCode(transaction).equals(transactionTypeCode) && purchasedDate(transaction).equals(expectedDate)) {
                return transaction.getId();
            }
        }
        return null;
    }

    private void verifyTransactionStatus(Set<GetAccountsPurchasedShares> transactions, String transactionTypeCode, String expectedDate,
            String expectedStatus) {
        boolean transactionFound = false;

        for (GetAccountsPurchasedShares transaction : transactions) {
            if (typeCode(transaction).equals(transactionTypeCode) && purchasedDate(transaction).equals(expectedDate)) {
                Assertions.assertEquals(expectedStatus, statusCode(transaction));
                transactionFound = true;
                break;
            }
        }

        Assertions.assertTrue(transactionFound,
                String.format("Transaction with type %s for %s should exist", transactionTypeCode, expectedDate));
    }

    private void verifyTransactionWithShares(Set<GetAccountsPurchasedShares> transactions, String transactionTypeCode, String expectedDate,
            int expectedShares, String expectedStatus) {
        boolean transactionFound = false;

        for (GetAccountsPurchasedShares transaction : transactions) {
            if (typeCode(transaction).equals(transactionTypeCode) && purchasedDate(transaction).equals(expectedDate)) {
                Assertions.assertEquals(expectedShares, transaction.getNumberOfShares());
                Assertions.assertEquals(expectedStatus, statusCode(transaction));
                transactionFound = true;
                break;
            }
        }

        Assertions.assertTrue(transactionFound, String.format("Transaction for %s should be successfully created", expectedDate));
    }

    private Long setupAndActivateShareAccount(Integer clientId, Integer productId, Integer savingsAccountId, String initialDate) {
        final Long shareAccountId = createShareAccount(clientId, productId, savingsAccountId);
        Assertions.assertNotNull(shareAccountId);

        updateShareAccount(shareAccountId, 25, initialDate, null);
        shareAccountHelper.approve(shareAccountId, initialDate, "Share Account Approval Note", DATE_FORMAT, "en");
        shareAccountHelper.activate(shareAccountId, initialDate, DATE_FORMAT, "en");

        // Verify account is active
        GetAccountsTypeAccountIdResponse shareAccountData = shareAccountHelper.getShareAccount(shareAccountId);
        Assertions.assertEquals("shareAccountStatusType.active", shareAccountData.getStatus().getCode());

        return shareAccountId;
    }

    private static String typeCode(GetAccountsPurchasedShares transaction) {
        return transaction.getType().getCode();
    }

    private static String statusCode(GetAccountsPurchasedShares transaction) {
        return transaction.getStatus().getCode();
    }

    private static String chargeTimeTypeCode(GetAccountsCharges charge) {
        return charge.getChargeTimeType().getCode();
    }

    private static String purchasedDate(GetAccountsPurchasedShares transaction) {
        return format(transaction.getPurchasedDate());
    }

    private static String format(LocalDate date) {
        Assertions.assertNotNull(date);
        return date.format(Utils.dateFormatter);
    }

    /** Compares by value so {@code 0}, {@code 0.0} and {@code 0.00} from the server all satisfy {@code "0"}. */
    private static void assertAmount(String expected, Number actual) {
        Assertions.assertNotNull(actual, "amount");
        BigDecimal actualAmount = new BigDecimal(actual.toString());
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actualAmount), () -> "expected " + expected + " but was " + actual);
    }
}
