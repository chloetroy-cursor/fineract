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
package org.apache.fineract.integrationtests.interoperation;

import static org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper.ACCOUNT_TYPE_INDIVIDUAL;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.internal.common.path.ObjectConverter;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.UUID;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.InteropIdentifierAccountResponseData;
import org.apache.fineract.client.models.InteropIdentifierRequestData;
import org.apache.fineract.client.models.InteropQuoteRequestData;
import org.apache.fineract.client.models.InteropQuoteResponseData;
import org.apache.fineract.client.models.InteropTransactionRequestData;
import org.apache.fineract.client.models.InteropTransactionRequestResponseData;
import org.apache.fineract.client.models.InteropTransactionTypeData;
import org.apache.fineract.client.models.InteropTransferRequestData;
import org.apache.fineract.client.models.InteropTransferResponseData;
import org.apache.fineract.client.models.MoneyData;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignInteropHelper;
import org.apache.fineract.integrationtests.client.feign.modules.FeignErrors;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsStatusChecker;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteropTest {

    private static final Logger LOG = LoggerFactory.getLogger(InteropTest.class);

    private static final String MIN_INTEREST_CALCULATON_BALANCE = null;
    private static final String MIN_REQUIRED_BALANCE = null;
    private static final String MIN_OPENING_BALANCE = "100000.0";
    private static final boolean ENFORCE_MIN_REQUIRED_BALANCE = false;
    private static final MathContext MATHCONTEXT = new MathContext(12, RoundingMode.HALF_EVEN);
    private static final String PARAM_ACCOUNT_BALANCE = "accountBalance";
    private static final String CURRENCY = "TZS";
    private static final BigDecimal AMOUNT = BigDecimal.TEN;
    private static final BigDecimal FEE = BigDecimal.ONE;
    private static final String NOTE = "Integration test";

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;

    private AccountHelper accountHelper;
    private SavingsAccountHelper savingsAccountHelper;
    private FeignInteropHelper interopHelper;

    private String savingsExternalId;
    private String transactionCode;
    private Integer clientId;
    private Integer savingsProductId;
    private Integer savingsId;
    private Integer chargeId;
    private String requestCode;
    private String quoteCode;
    private String transferCode;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());

        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        savingsExternalId = UUID.randomUUID().toString();
        transactionCode = UUID.randomUUID().toString();

        accountHelper = new AccountHelper(this.requestSpec, this.responseSpec);
        savingsAccountHelper = new SavingsAccountHelper(requestSpec, responseSpec);
        interopHelper = new FeignInteropHelper(FineractFeignClientHelper.getFineractFeignClient());
    }

    @Test
    public void testValidateAction() {
        InteropTransferRequestData request = transferRequest(UUID.randomUUID().toString(),
                InteropTransferRequestData.TransactionRoleEnum.PAYER);

        CallFailedRuntimeException missingAction = interopHelper.performTransferExpectingError(null, request);
        Assertions.assertEquals(400, missingAction.getStatus());
        Assertions.assertEquals("validation.msg.InteropApi.action.cannot.be.blank", FeignErrors.errorGlobalisationCode(missingAction));

        CallFailedRuntimeException unknownAction = interopHelper.performTransferExpectingError("UNKNOWN", request);
        Assertions.assertEquals(400, unknownAction.getStatus());
        Assertions.assertEquals("validation.msg.InteropApi.action.is.not.one.of.expected.enumerations",
                FeignErrors.errorGlobalisationCode(unknownAction));
    }

    @Test
    public void testInteroperation() {
        createClient();
        createSavingsProduct();
        createCharge();
        openSavingsAccount();

        testParties();
        testRequests();
        testQuotes();
        testTransfers();
    }

    private void createClient() {
        clientId = ClientHelper.createClient(requestSpec, responseSpec);
        Assertions.assertNotNull(clientId);
    }

    private void createSavingsProduct() {
        LOG.debug("------------------------------ Create Interoperable Saving Product ---------------------------------------");

        Account[] accounts = { accountHelper.createAssetAccount(), accountHelper.createIncomeAccount(),
                accountHelper.createExpenseAccount(), accountHelper.createLiabilityAccount() };

        SavingsProductHelper savingsProductHelper = new SavingsProductHelper();
        final String savingsProductJSON = savingsProductHelper.withCurrencyCode(CURRENCY).withNominalAnnualInterestRate(BigDecimal.ZERO)
                .withInterestCompoundingPeriodTypeAsDaily().withInterestPostingPeriodTypeAsMonthly()
                .withInterestCalculationPeriodTypeAsDailyBalance().withMinBalanceForInterestCalculation(MIN_INTEREST_CALCULATON_BALANCE)
                .withMinRequiredBalance(MIN_REQUIRED_BALANCE).withEnforceMinRequiredBalance(Boolean.toString(ENFORCE_MIN_REQUIRED_BALANCE))
                .withMinimumOpenningBalance(MIN_OPENING_BALANCE).withAccountingRuleAsCashBased(accounts).build();
        savingsProductId = SavingsProductHelper.createSavingsProduct(savingsProductJSON, requestSpec, responseSpec);
        Assertions.assertNotNull(savingsProductId);

        LOG.debug("Sucessfully created Interoperable Saving Product (id: {})", savingsProductId);
    }

    private void createCharge() {
        chargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsJSON(FEE.toString(), CURRENCY, ChargeTimeType.WITHDRAWAL_FEE));
        Assertions.assertNotNull(chargeId);
    }

    private void openSavingsAccount() {
        LOG.debug("------------------------------ Create Interoperable Saving Account ---------------------------------------");
        savingsId = savingsAccountHelper.applyForSavingsApplicationWithExternalId(clientId, savingsProductId, ACCOUNT_TYPE_INDIVIDUAL,
                savingsExternalId, true);
        Assertions.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(requestSpec, responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        if (chargeId != null) {
            savingsAccountHelper.addChargesForSavings(savingsId, chargeId, false, FEE);
        }

        LOG.debug("Sucessfully created Interoperable Saving Account (id: {})", savingsId);
    }

    private void testParties() {
        String idValue = UUID.randomUUID().toString();
        InteropIdentifierAccountResponseData party = interopHelper.registerAccountIdentifier(InteropIdentifierRequestData.IdTypeEnum.MSISDN,
                idValue, savingsExternalId);
        Assertions.assertEquals(savingsExternalId, party.getAccountId());

        CallFailedRuntimeException duplicate = interopHelper
                .registerAccountIdentifierExpectingError(InteropIdentifierRequestData.IdTypeEnum.MSISDN, idValue, savingsExternalId);
        Assertions.assertEquals(403, duplicate.getStatus());
        Assertions.assertEquals("error.msg.interop.duplicate.account.identifier", FeignErrors.errorGlobalisationCode(duplicate));

        party = interopHelper.getAccountByIdentifier(InteropIdentifierRequestData.IdTypeEnum.MSISDN, idValue);
        Assertions.assertEquals(savingsExternalId, party.getAccountId());

        party = interopHelper.deleteAccountIdentifier(InteropIdentifierRequestData.IdTypeEnum.MSISDN, idValue, savingsExternalId);
        Assertions.assertEquals(savingsExternalId, party.getAccountId());

        CallFailedRuntimeException deleted = interopHelper
                .getAccountByIdentifierExpectingError(InteropIdentifierRequestData.IdTypeEnum.MSISDN, idValue);
        Assertions.assertEquals(404, deleted.getStatus());
        Assertions.assertEquals("error.msg.resource.not.found", deleted.getUserMessageGlobalisationCode());
    }

    private void testRequests() {
        requestCode = UUID.randomUUID().toString();
        InteropTransactionRequestResponseData response = interopHelper
                .createTransactionRequest(transactionRequest(requestCode, InteropTransactionRequestData.TransactionRoleEnum.PAYER));
        Assertions.assertEquals(requestCode, response.getRequestCode());
        Assertions.assertEquals(InteropTransactionRequestResponseData.StateEnum.ACCEPTED, response.getState());

        // PAYEE role is not valid for a transaction request
        CallFailedRuntimeException payeeRequest = interopHelper.createTransactionRequestExpectingError(
                transactionRequest(requestCode, InteropTransactionRequestData.TransactionRoleEnum.PAYEE));
        Assertions.assertEquals(400, payeeRequest.getStatus());
        Assertions.assertEquals("validation.msg.interoperation.request.transactionRole.is.not.one.of.expected.enumerations",
                FeignErrors.errorGlobalisationCode(payeeRequest));
    }

    private void testQuotes() {
        // payer
        quoteCode = UUID.randomUUID().toString();
        InteropQuoteResponseData response = interopHelper
                .createQuote(quoteRequest(quoteCode, InteropQuoteRequestData.TransactionRoleEnum.PAYER));
        Assertions.assertEquals(quoteCode, response.getQuoteCode());
        Assertions.assertEquals(InteropQuoteResponseData.StateEnum.ACCEPTED, response.getState());

        MoneyData fee = response.getFspFee();
        Assertions.assertNotNull(fee);
        Assertions.assertTrue(MathUtil.isEqualTo(FEE, fee.getAmount()), "Quote fee expected: " + FEE + ", actual: " + fee.getAmount());
        Assertions.assertEquals(CURRENCY, fee.getCurrency());

        // payee
        response = interopHelper.createQuote(quoteRequest(quoteCode, InteropQuoteRequestData.TransactionRoleEnum.PAYEE));
        Assertions.assertEquals(quoteCode, response.getQuoteCode());
        Assertions.assertEquals(InteropQuoteResponseData.StateEnum.ACCEPTED, response.getState());

        fee = response.getFspFee();
        if (fee != null) {
            Assertions.assertTrue(MathUtil.isZero(fee.getAmount()),
                    "PAYEE Quote fee expected: " + BigDecimal.ZERO + ", actual: " + fee.getAmount());
        }
    }

    private void testTransfers() {
        String savings = (String) savingsAccountHelper.getSavingsAccountDetail(savingsId, null);
        JsonPath savingsJson = JsonPath.from(savings);
        BigDecimal onHold = ObjectConverter.convertObjectTo(savingsJson.get(SavingsApiConstants.savingsAmountOnHold), BigDecimal.class);
        BigDecimal balance = ObjectConverter.convertObjectTo(savingsJson.get(PARAM_ACCOUNT_BALANCE), BigDecimal.class);

        transferCode = UUID.randomUUID().toString();
        InteropTransferResponseData response = interopHelper
                .prepareTransfer(transferRequest(transferCode, InteropTransferRequestData.TransactionRoleEnum.PAYER));
        Assertions.assertEquals(transferCode, response.getTransferCode());
        Assertions.assertEquals(InteropTransferResponseData.StateEnum.ACCEPTED, response.getState());

        // prepare
        savings = (String) savingsAccountHelper.getSavingsAccountDetail(savingsId, null);
        LOG.debug("Response Interoperable GET Saving: {}", savings);
        savingsJson = JsonPath.from(savings);
        BigDecimal onHold2 = ObjectConverter.convertObjectTo(savingsJson.get(SavingsApiConstants.savingsAmountOnHold), BigDecimal.class);
        BigDecimal balance2 = ObjectConverter.convertObjectTo(savingsJson.get(PARAM_ACCOUNT_BALANCE), BigDecimal.class);

        BigDecimal transferAmount = AMOUNT.add(FEE);
        BigDecimal expectedHold = MathUtil.add(onHold, transferAmount, MATHCONTEXT);
        Assertions.assertTrue(MathUtil.isEqualTo(expectedHold, onHold2),
                "On hold amount expected: " + expectedHold + ", actual: " + onHold2);
        BigDecimal expectedBalance = MathUtil.subtract(balance, transferAmount, MATHCONTEXT);
        Assertions.assertTrue(MathUtil.isEqualTo(expectedBalance, balance2),
                "Balance amount expected: " + expectedBalance + ", actual: " + balance2);

        // payer
        response = interopHelper.createTransfer(transferRequest(transferCode, InteropTransferRequestData.TransactionRoleEnum.PAYER));
        Assertions.assertEquals(transferCode, response.getTransferCode());
        Assertions.assertEquals(InteropTransferResponseData.StateEnum.ACCEPTED, response.getState());

        savings = (String) savingsAccountHelper.getSavingsAccountDetail(savingsId, null);
        LOG.debug("Response Interoperable GET Saving: {}", savings);
        savingsJson = JsonPath.from(savings);
        BigDecimal onHold3 = ObjectConverter.convertObjectTo(savingsJson.get(SavingsApiConstants.savingsAmountOnHold), BigDecimal.class);
        BigDecimal balance3 = ObjectConverter.convertObjectTo(savingsJson.get(PARAM_ACCOUNT_BALANCE), BigDecimal.class);
        Assertions.assertTrue(MathUtil.isEqualTo(onHold, onHold3), "On hold amount expected: " + onHold + ", actual: " + onHold3);
        Assertions.assertTrue(MathUtil.isEqualTo(expectedBalance, balance3),
                "Balance amount expected: " + expectedBalance + ", actual: " + balance3);

        // payee
        response = interopHelper.createTransfer(transferRequest(transferCode, InteropTransferRequestData.TransactionRoleEnum.PAYEE));
        Assertions.assertEquals(transferCode, response.getTransferCode());
        Assertions.assertEquals(InteropTransferResponseData.StateEnum.ACCEPTED, response.getState());

        savings = (String) savingsAccountHelper.getSavingsAccountDetail(savingsId, null);
        LOG.debug("Response Interoperable GET Saving: {}", savings);
        savingsJson = JsonPath.from(savings);
        BigDecimal onHold4 = ObjectConverter.convertObjectTo(savingsJson.get(SavingsApiConstants.savingsAmountOnHold), BigDecimal.class);
        BigDecimal balance4 = ObjectConverter.convertObjectTo(savingsJson.get(PARAM_ACCOUNT_BALANCE), BigDecimal.class);
        expectedBalance = MathUtil.subtract(balance, FEE, MATHCONTEXT);
        Assertions.assertTrue(MathUtil.isEqualTo(onHold, onHold4), "On hold amount expected: " + onHold + ", actual: " + onHold4);
        Assertions.assertTrue(MathUtil.isEqualTo(balance, balance4),
                "Balance amount expected: " + expectedBalance + ", actual: " + balance4);
    }

    private InteropTransactionRequestData transactionRequest(String requestCode, InteropTransactionRequestData.TransactionRoleEnum role) {
        return new InteropTransactionRequestData().transactionCode(transactionCode).requestCode(requestCode).accountId(savingsExternalId)
                .transactionRole(role).amount(money(AMOUNT)).transactionType(paymentBy(InteropTransactionTypeData.InitiatorEnum.PAYEE));
    }

    private InteropQuoteRequestData quoteRequest(String quoteCode, InteropQuoteRequestData.TransactionRoleEnum role) {
        return new InteropQuoteRequestData().transactionCode(transactionCode).quoteCode(quoteCode).accountId(savingsExternalId)
                .transactionRole(role).amountType(InteropQuoteRequestData.AmountTypeEnum.RECEIVE).note(NOTE).amount(money(AMOUNT))
                .transactionType(paymentBy(InteropTransactionTypeData.InitiatorEnum.PAYER));
    }

    private InteropTransferRequestData transferRequest(String transferCode, InteropTransferRequestData.TransactionRoleEnum role) {
        InteropTransferRequestData request = new InteropTransferRequestData().transactionCode(transactionCode).transferCode(transferCode)
                .accountId(savingsExternalId).transactionRole(role).note(NOTE).amount(money(AMOUNT))
                .transactionType(paymentBy(InteropTransactionTypeData.InitiatorEnum.PAYER));
        // the payer side is a withdrawal, which carries the FSP fee
        if (role == InteropTransferRequestData.TransactionRoleEnum.PAYER) {
            request.fspFee(money(FEE));
        }
        return request;
    }

    private static MoneyData money(BigDecimal amount) {
        return new MoneyData().amount(amount).currency(CURRENCY);
    }

    private static InteropTransactionTypeData paymentBy(InteropTransactionTypeData.InitiatorEnum initiator) {
        return new InteropTransactionTypeData().scenario(InteropTransactionTypeData.ScenarioEnum.PAYMENT).initiator(initiator)
                .initiatorType(InteropTransactionTypeData.InitiatorTypeEnum.CONSUMER);
    }
}
