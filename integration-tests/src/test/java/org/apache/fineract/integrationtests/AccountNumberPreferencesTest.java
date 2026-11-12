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
package org.apache.fineract.integrationtests;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.GetAccountNumberFormatsIdResponse;
import org.apache.fineract.client.models.PutAccountNumberFormatsResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignAccountNumberFormatHelper;
import org.apache.fineract.integrationtests.client.feign.modules.FeignErrors;
import org.apache.fineract.integrationtests.common.CenterDomain;
import org.apache.fineract.integrationtests.common.CenterHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(LoanTestLifecycleExtension.class)
public class AccountNumberPreferencesTest {

    private static final Logger LOG = LoggerFactory.getLogger(AccountNumberPreferencesTest.class);
    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private Integer clientId;
    private Integer loanProductId;
    private Integer loanId;
    private Integer savingsProductId;
    private Integer savingsId;
    private final String loanPrincipalAmount = "100000.00";
    private final String numberOfRepayments = "12";
    private final String interestRatePerPeriod = "18";
    private final String dateString = "04 September 2014";
    private final String minBalanceForInterestCalculation = null;
    private final String minRequiredBalance = null;
    private final String enforceMinRequiredBalance = "false";
    private LoanTransactionHelper loanTransactionHelper;
    private SavingsAccountHelper savingsAccountHelper;
    private final FeignAccountNumberFormatHelper accountNumberFormatHelper = new FeignAccountNumberFormatHelper(
            FineractFeignClientHelper.getFineractFeignClient());
    private Long clientAccountNumberPreferenceId;
    private Long loanAccountNumberPreferenceId;
    private Long savingsAccountNumberPreferenceId;
    private Long groupsAccountNumberPreferenceId;
    private Long centerAccountNumberPreferenceId;
    private static final String MINIMUM_OPENING_BALANCE = "1000.0";
    private static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    private Boolean isAccountPreferenceSetUp = false;
    private Integer clientTypeCodeId;
    private String clientCodeValueName;
    private Integer clientCodeValueId;
    private final String clientTypeName = "CLIENT_TYPE";
    private final String officeName = "OFFICE_NAME";
    private final String loanShortName = "LOAN_PRODUCT_SHORT_NAME";
    private final String savingsShortName = "SAVINGS_PRODUCT_SHORT_NAME";
    private Integer groupID;
    private Integer centerId;
    private String groupAccountNo;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
    }

    @Test
    public void testAccountNumberPreferences() {

        /* Create Loan and Savings Product */
        this.createLoanAndSavingsProduct();

        /* Ensure no account number preferences are present in the system */
        this.deleteAllAccountNumberPreferences();

        /*
         * Validate the default account number generation rules for clients, loans and savings accounts.
         */
        this.validateDefaultAccountNumberGeneration();

        /* Create and Validate account number preferences */
        this.createAccountNumberPreference();

        /*
         * Validate account number preference rules apply to Clients,Loans and Saving Accounts
         */
        this.validateAccountNumberGenerationWithPreferences();

        /* Validate account number preferences Updation */
        this.updateAccountNumberPreference();

        /*
         * Validate account number preference rules apply to Clients,Loans and Saving Accounts after Updation
         */
        this.validateAccountNumberGenerationWithPreferences();

        /* Delete all account number preferences */
        this.deleteAllAccountNumberPreferences();

    }

    private void createLoanAndSavingsProduct() {
        this.createLoanProduct();
        this.createSavingsProduct();
    }

    private void deleteAllAccountNumberPreferences() {
        List<GetAccountNumberFormatsIdResponse> preferences = this.accountNumberFormatHelper.retrieveAllAccountNumberFormats();
        /* Deletion of valid account preference ID */
        for (GetAccountNumberFormatsIdResponse preference : preferences) {
            Long deletedId = this.accountNumberFormatHelper.deleteAccountNumberFormat(preference.getId()).getResourceId();
            LOG.info("Successfully deleted account number preference (ID: {} )", deletedId);
        }
        /* Deletion of invalid account preference ID should fail */
        LOG.info(
                "---------------------------------DELETING ACCOUNT NUMBER PREFERENCE WITH INVALID ID------------------------------------------");

        CallFailedRuntimeException deletionError = this.accountNumberFormatHelper.deleteAccountNumberFormatExpectingError(10L);
        Assertions.assertEquals(404, deletionError.getStatus());
        Assertions.assertEquals("error.msg.resource.not.found", FeignErrors.errorGlobalisationCode(deletionError));
    }

    private void validateDefaultAccountNumberGeneration() {
        this.createAndValidateClientEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateLoanEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateSavingsEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateGroup(this.isAccountPreferenceSetUp);
        this.createAndValidateCenter(this.isAccountPreferenceSetUp);
    }

    private void validateAccountNumberGenerationWithPreferences() {
        this.isAccountPreferenceSetUp = true;
        this.createAndValidateClientEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateLoanEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateSavingsEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateGroup(this.isAccountPreferenceSetUp);
        this.createAndValidateCenter(this.isAccountPreferenceSetUp);
    }

    private void createAccountNumberPreference() {
        this.clientAccountNumberPreferenceId = this.accountNumberFormatHelper.createAccountNumberFormat(
                FeignAccountNumberFormatHelper.ACCOUNT_TYPE_CLIENT, FeignAccountNumberFormatHelper.PREFIX_TYPE_CLIENT_TYPE);
        LOG.info("Successfully created account number preferences for Client (ID: {})", this.clientAccountNumberPreferenceId);

        this.loanAccountNumberPreferenceId = this.accountNumberFormatHelper.createAccountNumberFormat(
                FeignAccountNumberFormatHelper.ACCOUNT_TYPE_LOAN, FeignAccountNumberFormatHelper.PREFIX_TYPE_OFFICE_NAME);
        LOG.info("Successfully created account number preferences for Loan (ID: {} )", this.loanAccountNumberPreferenceId);

        this.savingsAccountNumberPreferenceId = this.accountNumberFormatHelper.createAccountNumberFormat(
                FeignAccountNumberFormatHelper.ACCOUNT_TYPE_SAVINGS, FeignAccountNumberFormatHelper.PREFIX_TYPE_OFFICE_NAME);
        LOG.info("Successfully created account number preferences for Savings (ID: {})", this.savingsAccountNumberPreferenceId);

        this.groupsAccountNumberPreferenceId = this.accountNumberFormatHelper.createAccountNumberFormat(
                FeignAccountNumberFormatHelper.ACCOUNT_TYPE_GROUP, FeignAccountNumberFormatHelper.PREFIX_TYPE_OFFICE_NAME);
        LOG.info("Successfully created account number preferences for Groups (ID: {})", this.groupsAccountNumberPreferenceId);

        this.centerAccountNumberPreferenceId = this.accountNumberFormatHelper.createAccountNumberFormat(
                FeignAccountNumberFormatHelper.ACCOUNT_TYPE_CENTER, FeignAccountNumberFormatHelper.PREFIX_TYPE_OFFICE_NAME);
        LOG.info("Successfully created account number preferences for Center (ID: {})", this.centerAccountNumberPreferenceId);

        for (Long preferenceId : List.of(this.clientAccountNumberPreferenceId, this.loanAccountNumberPreferenceId,
                this.savingsAccountNumberPreferenceId, this.groupsAccountNumberPreferenceId, this.centerAccountNumberPreferenceId)) {
            Assertions.assertEquals(preferenceId, this.accountNumberFormatHelper.retrieveAccountNumberFormat(preferenceId).getId());
        }

        this.createAccountNumberPreferenceInvalidData(1000L, 1001L);
        this.createAccountNumberPreferenceDuplicateData(FeignAccountNumberFormatHelper.ACCOUNT_TYPE_CLIENT,
                FeignAccountNumberFormatHelper.PREFIX_TYPE_CLIENT_TYPE);

    }

    private void createAccountNumberPreferenceDuplicateData(final long accountType, final long prefixType) {
        /* Creating account Preference with duplicate data should fail */
        LOG.info(
                "---------------------------------CREATING ACCOUNT NUMBER PREFERENCE WITH DUPLICATE DATA------------------------------------------");

        CallFailedRuntimeException creationError = this.accountNumberFormatHelper.createAccountNumberFormatExpectingError(accountType,
                prefixType);

        Assertions.assertEquals(403, creationError.getStatus());
        Assertions.assertEquals("error.msg.account.number.format.duplicate.account.type",
                FeignErrors.errorGlobalisationCode(creationError));

    }

    private void createAccountNumberPreferenceInvalidData(final long accountType, final long prefixType) {

        /* Creating account Preference with invalid data should fail */
        LOG.info(
                "---------------------------------CREATING ACCOUNT NUMBER PREFERENCE WITH INVALID DATA------------------------------------------");

        CallFailedRuntimeException creationError = this.accountNumberFormatHelper.createAccountNumberFormatExpectingError(accountType,
                prefixType);

        Assertions.assertEquals(400, creationError.getStatus());
        String errorCode = FeignErrors.errorGlobalisationCode(creationError);
        Assertions.assertTrue(
                Set.of("validation.msg.accountNumberFormat.accountType.is.not.within.expected.range",
                        "validation.msg.accountNumberFormat.prefixType.is.not.one.of.expected.enumerations").contains(errorCode),
                errorCode);
    }

    private void updateAccountNumberPreference() {
        PutAccountNumberFormatsResponse accountNumberPreferences = this.accountNumberFormatHelper
                .updateAccountNumberFormat(this.clientAccountNumberPreferenceId, FeignAccountNumberFormatHelper.PREFIX_TYPE_CLIENT_TYPE);

        LOG.info("--------------------------UPDATION SUCCESSFUL FOR ACCOUNT NUMBER PREFERENCE ID {}",
                accountNumberPreferences.getResourceId());

        Assertions.assertEquals(accountNumberPreferences.getResourceId(),
                this.accountNumberFormatHelper.retrieveAccountNumberFormat(accountNumberPreferences.getResourceId()).getId());

        /* Update invalid account preference id should fail */
        LOG.info(
                "---------------------------------UPDATING ACCOUNT NUMBER PREFERENCE WITH INVALID DATA------------------------------------------");

        /* Invalid Account Type */
        CallFailedRuntimeException updationError = this.accountNumberFormatHelper.updateAccountNumberFormatExpectingError(9999L,
                FeignAccountNumberFormatHelper.PREFIX_TYPE_CLIENT_TYPE);
        Assertions.assertEquals(404, updationError.getStatus());
        Assertions.assertEquals("error.msg.resource.not.found", FeignErrors.errorGlobalisationCode(updationError));

        /* Invalid Prefix Type */
        CallFailedRuntimeException updationError1 = this.accountNumberFormatHelper
                .updateAccountNumberFormatExpectingError(this.clientAccountNumberPreferenceId, 103L);

        Assertions.assertEquals(400, updationError1.getStatus());
        Assertions.assertEquals("validation.msg.validation.errors.exist", updationError1.getUserMessageGlobalisationCode());

    }

    private String prefixTypeValue(Long accountNumberPreferenceId) {
        return this.accountNumberFormatHelper.retrieveAccountNumberFormat(accountNumberPreferenceId).getPrefixType().getValue();
    }

    private void createAndValidateClientEntity(Boolean isAccountPreferenceSetUp) {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        if (isAccountPreferenceSetUp) {
            this.createAndValidateClientBasedOnAccountPreference();
        } else {
            this.createAndValidateClientWithoutAccountPreference();
        }
    }

    private void createAndValidateGroup(Boolean isAccountPreferenceSetUp) {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.groupID = GroupHelper.createGroup(this.requestSpec, this.responseSpec);
        GroupHelper.verifyGroupCreatedOnServer(this.requestSpec, this.responseSpec, groupID);

        this.groupID = GroupHelper.activateGroup(this.requestSpec, this.responseSpec, groupID.toString());
        GroupHelper.verifyGroupActivatedOnServer(this.requestSpec, this.responseSpec, groupID, true);

        final String GROUP_URL = "/fineract-provider/api/v1/groups/" + this.groupID + "?" + Utils.TENANT_IDENTIFIER;
        this.groupAccountNo = Utils.performServerGet(requestSpec, responseSpec, GROUP_URL, "accountNo");

        if (isAccountPreferenceSetUp) {
            String groupsPrefixName = prefixTypeValue(this.groupsAccountNumberPreferenceId);

            if (groupsPrefixName.equals(this.officeName)) {

                final String groupOfficeName = Utils.performServerGet(requestSpec, responseSpec, GROUP_URL, "officeName");

                this.validateAccountNumberLengthAndStartsWithPrefix(this.groupAccountNo, groupOfficeName);
            }
        } else {
            validateAccountNumberLengthAndStartsWithPrefix(this.groupAccountNo, null);
        }
    }

    private void createAndValidateCenter(Boolean isAccountPreferenceSetUp) {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        Integer officeId = new OfficeHelper().createOffice(LocalDate.of(2007, 7, 1)).getResourceId().intValue();

        String name = "CenterCreation" + new Timestamp(new java.util.Date().getTime());
        this.centerId = CenterHelper.createCenter(name, officeId, requestSpec, responseSpec);
        CenterDomain center = CenterHelper.retrieveByID(centerId, requestSpec, responseSpec);
        Assertions.assertNotNull(center);
        Assertions.assertTrue(center.getName().equals(name));

        if (isAccountPreferenceSetUp) {
            String centerPrefixName = prefixTypeValue(this.centerAccountNumberPreferenceId);
            final String CENTER_URL = "/fineract-provider/api/v1/centers/" + this.centerId + "?" + Utils.TENANT_IDENTIFIER;

            if (centerPrefixName.equals(this.officeName)) {
                final String centerOfficeName = Utils.performServerGet(requestSpec, responseSpec, CENTER_URL, "officeName");
                this.validateAccountNumberLengthAndStartsWithPrefix(center.getAccountNo(), centerOfficeName);
            }
        } else {
            validateAccountNumberLengthAndStartsWithPrefix(center.getAccountNo(), null);
        }
    }

    private void createAndValidateClientWithoutAccountPreference() {
        this.clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(this.clientId);
        String clientAccountNo = (String) ClientHelper.getClient(requestSpec, responseSpec, this.clientId.toString(), "accountNo");
        validateAccountNumberLengthAndStartsWithPrefix(clientAccountNo, null);
    }

    private void createAndValidateClientBasedOnAccountPreference() {
        final String codeName = "ClientType";
        String clientAccountNo = null;
        String clientPrefixName = prefixTypeValue(this.clientAccountNumberPreferenceId);
        if (clientPrefixName.equals(this.clientTypeName)) {

            /* Retrieve Code id for the Code "ClientType" */
            HashMap<String, Object> code = CodeHelper.getCodeByName(this.requestSpec, this.responseSpec, codeName);
            this.clientTypeCodeId = (Integer) code.get("id");

            /* Retrieve/Create Code Values for the Code "ClientType" */
            HashMap<String, Object> codeValue = CodeHelper.retrieveOrCreateCodeValue(this.clientTypeCodeId, this.requestSpec,
                    this.responseSpec);

            this.clientCodeValueName = (String) codeValue.get("name");
            this.clientCodeValueId = (Integer) codeValue.get("id");

            /* Create Client with Client Type */
            this.clientId = ClientHelper.createClientForAccountPreference(this.requestSpec, this.responseSpec, this.clientCodeValueId,
                    "clientId");
            ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, this.clientId);

            // Assertions.assertNotNull(clientId);

            clientAccountNo = (String) ClientHelper.getClient(this.requestSpec, this.responseSpec, this.clientId.toString(), "accountNo");
            this.validateAccountNumberLengthAndStartsWithPrefix(clientAccountNo, this.clientCodeValueName);

        } else if (clientPrefixName.equals(this.officeName)) {
            this.clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);
            ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, this.clientId);
            // Assertions.assertNotNull(clientId);
            clientAccountNo = (String) ClientHelper.getClient(requestSpec, responseSpec, this.clientId.toString(), "accountNo");
            String officeName = (String) ClientHelper.getClient(requestSpec, responseSpec, this.clientId.toString(), "officeName");
            this.validateAccountNumberLengthAndStartsWithPrefix(clientAccountNo, officeName);
        }
    }

    private void validateAccountNumberLengthAndStartsWithPrefix(final String accountNumber, String prefix) {
        if (prefix != null) {
            prefix = prefix.substring(0, Math.min(prefix.length(), 10));
            Assertions.assertEquals(accountNumber.length(), prefix.length() + 9);
            Assertions.assertTrue(accountNumber.startsWith(prefix));
        } else {
            Assertions.assertEquals(9, accountNumber.length());
        }
    }

    private void createLoanProduct() {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        LOG.info("---------------------------------CREATING LOAN PRODUCT------------------------------------------");

        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(loanPrincipalAmount)
                .withNumberOfRepayments(numberOfRepayments).withinterestRatePerPeriod(interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear().build(null);

        this.loanProductId = this.loanTransactionHelper.getLoanProductId(loanProductJSON);
        LOG.info("Successfully created loan product  (ID: {} )", this.loanProductId);
    }

    private void addCollaterals(List<HashMap> collaterals, Integer collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Integer collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    private void createAndValidateLoanEntity(Boolean isAccountPreferenceSetUp) {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        LOG.info("---------------------------------NEW LOAN APPLICATION------------------------------------------");
        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                this.clientId.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(loanPrincipalAmount)
                .withLoanTermFrequency(numberOfRepayments).withLoanTermFrequencyAsMonths().withNumberOfRepayments(numberOfRepayments)
                .withRepaymentEveryAfter("1").withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments()
                .withInterestCalculationPeriodTypeAsDays().withInterestRatePerPeriod(interestRatePerPeriod).withLoanTermFrequencyAsMonths()
                .withSubmittedOnDate(dateString).withExpectedDisbursementDate(dateString).withPrincipalGrace("2").withInterestGrace("2")
                .withCollaterals(collaterals).build(this.clientId.toString(), this.loanProductId.toString(), null);

        LOG.info("Loan Application :{}", loanApplicationJSON);

        this.loanId = this.loanTransactionHelper.getLoanId(loanApplicationJSON);
        String loanAccountNo = (String) this.loanTransactionHelper.getLoanDetail(this.requestSpec, this.responseSpec, this.loanId,
                "accountNo");

        if (isAccountPreferenceSetUp) {
            String loanPrefixName = prefixTypeValue(this.loanAccountNumberPreferenceId);
            if (loanPrefixName.equals(this.officeName)) {
                String loanOfficeName = (String) ClientHelper.getClient(requestSpec, responseSpec, this.clientId.toString(), "officeName");
                this.validateAccountNumberLengthAndStartsWithPrefix(loanAccountNo, loanOfficeName);
            } else if (loanPrefixName.equals(this.loanShortName)) {
                String loanShortName = (String) this.loanTransactionHelper.getLoanProductDetail(this.requestSpec, this.responseSpec,
                        this.loanProductId, "shortName");
                this.validateAccountNumberLengthAndStartsWithPrefix(loanAccountNo, loanShortName);
            }
            LOG.info("SUCCESSFULLY CREATED LOAN APPLICATION BASED ON ACCOUNT PREFERENCES (ID: {} )", this.loanId);
        } else {
            this.validateAccountNumberLengthAndStartsWithPrefix(loanAccountNo, null);
            LOG.info("SUCCESSFULLY CREATED LOAN APPLICATION (ID: {} )", loanId);
        }
    }

    private void createSavingsProduct() {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        LOG.info("------------------------------CREATING NEW SAVINGS PRODUCT ---------------------------------------");

        SavingsProductHelper savingsProductHelper = new SavingsProductHelper();

        final String savingsProductJSON = savingsProductHelper
                //
                .withInterestCompoundingPeriodTypeAsDaily()
                //
                .withInterestPostingPeriodTypeAsMonthly()
                //
                .withInterestCalculationPeriodTypeAsDailyBalance()
                //
                .withMinBalanceForInterestCalculation(minBalanceForInterestCalculation)
                //
                .withMinRequiredBalance(minRequiredBalance).withEnforceMinRequiredBalance(enforceMinRequiredBalance)
                .withMinimumOpenningBalance(MINIMUM_OPENING_BALANCE).build();
        this.savingsProductId = SavingsProductHelper.createSavingsProduct(savingsProductJSON, this.requestSpec, this.responseSpec);
        LOG.info("Sucessfully created savings product (ID: {} )", this.savingsProductId);

    }

    private void createAndValidateSavingsEntity(Boolean isAccountPreferenceSetUp) {
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        this.savingsId = this.savingsAccountHelper.applyForSavingsApplication(this.clientId, this.savingsProductId,
                ACCOUNT_TYPE_INDIVIDUAL);

        String savingsAccountNo = (String) this.savingsAccountHelper.getSavingsAccountDetail(this.savingsId, "accountNo");

        if (isAccountPreferenceSetUp) {
            String savingsPrefixName = prefixTypeValue(this.savingsAccountNumberPreferenceId);

            if (savingsPrefixName.equals(this.officeName)) {
                String savingsOfficeName = (String) ClientHelper.getClient(requestSpec, responseSpec, this.clientId.toString(),
                        "officeName");
                this.validateAccountNumberLengthAndStartsWithPrefix(savingsAccountNo, savingsOfficeName);
            } else if (savingsPrefixName.equals(this.savingsShortName)) {
                String loanShortName = (String) this.savingsAccountHelper.getSavingsAccountDetail(this.savingsId, "shortName");
                this.validateAccountNumberLengthAndStartsWithPrefix(savingsAccountNo, loanShortName);
            }
            LOG.info("SUCCESSFULLY CREATED SAVINGS APPLICATION BASED ON ACCOUNT PREFERENCES (ID:  {} )", this.loanId);
        } else {
            this.validateAccountNumberLengthAndStartsWithPrefix(savingsAccountNo, null);
            LOG.info("SUCCESSFULLY CREATED SAVINGS APPLICATION (ID:{} )", this.savingsId);
        }
    }
}
