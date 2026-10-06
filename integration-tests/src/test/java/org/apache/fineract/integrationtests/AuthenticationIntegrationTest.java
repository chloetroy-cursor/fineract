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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignLoanHelper;
import org.apache.fineract.integrationtests.client.feign.modules.LoanRequestBuilders;
import org.apache.fineract.integrationtests.client.feign.modules.LoanTestData;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith(LoanTestLifecycleExtension.class)
public class AuthenticationIntegrationTest {

    private static final String LOAN_DATE = "11 July 2022";
    private static final String APPROVE_COMMAND = "approve";
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private FeignLoanHelper loanHelper;
    private Integer loanID;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        setupAuthenticatedRequestSpec();
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanHelper = new FeignLoanHelper(FineractFeignClientHelper.getFineractFeignClient());

        AccountHelper accountHelper = new AccountHelper(this.requestSpec, this.responseSpec);
        Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        String username = Utils.uniqueRandomStringGenerator("user", 8);
        UserHelper.createUser(this.requestSpec, this.responseSpec, 1, staffId, username, "A1b2c3d4e5f$", "resourceId");
        Integer clientID = ClientHelper.createClient(requestSpec, responseSpec);

        Integer loanProductID = setupLoanProduct(accountHelper);
        final PostLoansRequest loanApplication = LoanRequestBuilders
                .legacyIndividualApplication(clientID.longValue(), loanProductID.longValue(), "10000", 6, new BigDecimal("2"), LOAN_DATE)
                .submittedOnDate("10 July 2022").interestType(LoanTestData.InterestType.FLAT);
        this.loanID = this.loanHelper.applyForLoan(loanApplication).getLoanId().intValue();
    }

    @Test
    public void shouldAllowAccessForAuthenticatedUser() {
        setupAuthenticatedRequestSpec();
        String loanApprovalCommand = createLoanApprovalCommand();
        String loanApprovalRequest = createLoanApprovalRequest();

        HashMap response = Utils.performServerPost(this.requestSpec, this.responseSpec, loanApprovalCommand, loanApprovalRequest,
                "changes");
        HashMap status = (HashMap) response.get("status");

        assertEquals(200, (Integer) status.get("id"));
    }

    @Test
    public void shouldReturnUnauthorizedForUnauthenticatedAccess() throws JsonProcessingException {
        setupUnauthenticatedRequestSpec();
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(401).build();

        String loanApprovalCommand = createLoanApprovalCommand();
        String loanApprovalRequest = createLoanApprovalRequest();

        String rawResponse = Utils.performServerPost(this.requestSpec, this.responseSpec, loanApprovalCommand, loanApprovalRequest, null);

        ObjectMapper objectMapper = new ObjectMapper();
        HashMap response = objectMapper.readValue(rawResponse, HashMap.class);

        assertEquals(401, (Integer) response.get("status"));
        assertEquals("Unauthorized", response.get("error"));
    }

    private Integer setupLoanProduct(AccountHelper accountHelper) {
        final Account[] accounts = { accountHelper.createAssetAccount(), accountHelper.createIncomeAccount(),
                accountHelper.createExpenseAccount(), accountHelper.createLiabilityAccount() };
        return this.loanHelper.createLoanProduct(new LoanProductTestBuilder().withPrincipal("10000000.00").withNumberOfRepayments("24")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("2")
                .withInterestRateFrequencyTypeAsMonths().withRepaymentStrategy(LoanProductTestBuilder.DEFAULT_STRATEGY)
                .withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsDecliningBalance().currencyDetails("0", "0")
                .withAccounting(LoanProductTestBuilder.CASH_BASED, accounts).buildRequest()).getResourceId().intValue();
    }

    private void setupAuthenticatedRequestSpec() {
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    private void setupUnauthenticatedRequestSpec() {
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
    }

    private String createLoanApprovalRequest() {
        return new Gson()
                .toJson(Map.of("locale", "en", "dateFormat", "dd MMMM yyyy", "approvedOnDate", LOAN_DATE, "note", "Approval NOTE"));
    }

    private String createLoanApprovalCommand() {
        return "/fineract-provider/api/v1/loans/" + loanID + "?command=" + APPROVE_COMMAND + "&" + Utils.TENANT_IDENTIFIER;
    }
}
