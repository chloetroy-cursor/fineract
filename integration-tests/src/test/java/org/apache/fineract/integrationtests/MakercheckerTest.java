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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.AuditData;
import org.apache.fineract.client.models.CommandProcessingResult;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.models.PutPermissionsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignMakerCheckerHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.system.DatatableHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings({ "unused" })
public class MakercheckerTest {

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private FeignMakerCheckerHelper makercheckersHelper;
    private RolesHelper rolesHelper;
    private SavingsProductHelper savingsProductHelper;
    private SavingsAccountHelper savingsAccountHelper;
    private static final String START_DATE_STRING = "03 June 2023";
    private static final String TRANSACTION_DATE_STRING = "05 June 2023";
    private GlobalConfigurationHelper globalConfigurationHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.makercheckersHelper = new FeignMakerCheckerHelper(FineractFeignClientHelper.getFineractFeignClient());
        this.rolesHelper = new RolesHelper();
        this.savingsProductHelper = new SavingsProductHelper();
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);
        this.globalConfigurationHelper = new GlobalConfigurationHelper();
    }

    @Test
    public void testMakercheckerInboxList() {
        // given
        // when
        List<AuditData> makerCheckerList = this.makercheckersHelper.getMakerCheckerList();
        assertNotNull(makerCheckerList);
    }

    @Test
    public void testMakerCheckerOn() {

        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(true));
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(false));

        try {
            // client permission - maker-checker disabled
            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_CLIENT", false);
            rolesHelper.updatePermissions(putPermissionsRequest);
            putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("ACTIVATE_CLIENT", false);
            rolesHelper.updatePermissions(putPermissionsRequest);

            Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
            Map<String, Boolean> permissionMap = Map.of("CREATE_CLIENT", true, "CREATE_CLIENT_CHECKER", true, "ACTIVATE_CLIENT", true,
                    "ACTIVATE_CLIENT_CHECKER", true, "WITHDRAWAL_SAVINGSACCOUNT", true, "WITHDRAWAL_SAVINGSACCOUNT_CHECKER", true);
            RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, permissionMap);
            final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
            // create maker user
            String maker = Utils.uniqueRandomStringGenerator("user", 8);
            final Integer makerUserId = (Integer) UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, maker,
                    "A1b2c3d4e5f$", "resourceId");

            // create client - maker-checker disabled
            RequestSpecification makerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build()
                    .header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(maker, "A1b2c3d4e5f$"));
            Integer clientId = ClientHelper.createClient(makerRequestSpec, this.responseSpec);
            assertNotNull(clientId);
            ClientHelper.verifyClientCreatedOnServer(requestSpec, this.responseSpec, clientId);

            final Integer savingsId = createApproveActivateSavingsAccountDailyPosting(clientId, START_DATE_STRING);
            assertNotNull(savingsId);
            Integer transactionId = (Integer) savingsAccountHelper.depositToSavingsAccount(savingsId, "1000", TRANSACTION_DATE_STRING,
                    CommonConstants.RESPONSE_RESOURCE_ID);
            assertNotNull(transactionId);

            // client and saving permission - maker-checker enabled
            putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("ACTIVATE_CLIENT", true);
            rolesHelper.updatePermissions(putPermissionsRequest);
            putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("WITHDRAWAL_SAVINGSACCOUNT", true);
            rolesHelper.updatePermissions(putPermissionsRequest);

            // create client - maker-checker enabled
            clientId = ClientHelper.createClient(makerRequestSpec, this.responseSpec);
            assertNull(clientId, "Client is created on the server");

            List<AuditData> auditDetails = makercheckersHelper
                    .getMakerCheckerList(Map.of("actionName", "CREATE", "entityName", "CLIENT", "makerId", makerUserId));
            assertEquals(1, auditDetails.size(), "More than one command exists");
            Long clientCommandId = auditDetails.get(0).getId();

            // savings withdrawal - maker-checker enabled
            SavingsAccountHelper makerSavingsHelper = new SavingsAccountHelper(makerRequestSpec, this.responseSpec);
            Integer withdrawalId = (Integer) makerSavingsHelper.withdrawalFromSavingsAccount(savingsId, "100", TRANSACTION_DATE_STRING,
                    CommonConstants.RESPONSE_RESOURCE_ID);
            assertNull(withdrawalId, "Withdrawal performed on the server");

            auditDetails = makercheckersHelper
                    .getMakerCheckerList(Map.of("actionName", "WITHDRAWAL", "entityName", "SAVINGSACCOUNT", "makerId", makerUserId));
            assertEquals(1, auditDetails.size(), "More than one command exists");
            Long savingCommandId = auditDetails.get(0).getId();

            // check by the same user should fail
            FeignMakerCheckerHelper makerMakerCheckerHelper = new FeignMakerCheckerHelper(
                    FineractFeignClientHelper.createNewFineractFeignClient(maker, "A1b2c3d4e5f$"));
            assertEquals(400, makerMakerCheckerHelper.approveMakerCheckerEntryExpectingError(clientCommandId).getStatus());
            assertEquals(400, makerMakerCheckerHelper.approveMakerCheckerEntryExpectingError(savingCommandId).getStatus());

            // create checker user
            String checker = Utils.uniqueRandomStringGenerator("user", 8);
            final Integer checkerUserId = (Integer) UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, checker,
                    "A1b2c3d4e5f$", "resourceId");
            FeignMakerCheckerHelper checkerMakerCheckerHelper = new FeignMakerCheckerHelper(
                    FineractFeignClientHelper.createNewFineractFeignClient(checker, "A1b2c3d4e5f$"));

            // check by another checker user should succeed
            CommandProcessingResult response = checkerMakerCheckerHelper.approveMakerCheckerEntry(clientCommandId);
            assertNotNull(response);
            assertNotNull(response.getClientId());
            clientId = response.getClientId().intValue();
            ClientHelper.verifyClientCreatedOnServer(requestSpec, responseSpec, clientId);

            response = checkerMakerCheckerHelper.approveMakerCheckerEntry(savingCommandId);
            assertNotNull(response);
            assertNotNull(response.getResourceId());
            withdrawalId = response.getResourceId().intValue();

            // add checker superuser permission - actions are performed in one step
            permissionMap = Map.of("CHECKER_SUPER_USER", true);
            RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, permissionMap);
            clientId = ClientHelper.createClient(makerRequestSpec, this.responseSpec);
            assertNotNull(clientId);
            ClientHelper.verifyClientCreatedOnServer(requestSpec, this.responseSpec, clientId);

            withdrawalId = (Integer) makerSavingsHelper.withdrawalFromSavingsAccount(savingsId, "100", TRANSACTION_DATE_STRING,
                    CommonConstants.RESPONSE_RESOURCE_ID);
            assertNotNull(withdrawalId);
        } finally {

            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(false));

            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(true));

            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("WITHDRAWAL_SAVINGSACCOUNT",
                    false);
            rolesHelper.updatePermissions(putPermissionsRequest);
            putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("ACTIVATE_CLIENT", false);
            rolesHelper.updatePermissions(putPermissionsRequest);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "m_client", "m_group", "m_center", "m_loan", "m_office", "m_savings_account" })
    public void testRejectDatatableCreationCleansUpOrphanedTable(String apptableName) {

        // enable maker-checker globally
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(true));
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(false));

        try {
            // enable maker-checker for datatable creation
            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_DATATABLE", true);
            rolesHelper.updatePermissions(putPermissionsRequest);

            // create role with permissions for maker and checker
            Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
            Map<String, Boolean> permissionMap = Map.of("CREATE_DATATABLE", true, "CREATE_DATATABLE_CHECKER", true);
            RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, permissionMap);

            // create maker user
            Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
            String maker = Utils.uniqueRandomStringGenerator("user", 8);
            Integer makerUserId = (Integer) UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, maker,
                    "A1b2c3d4e5f$", "resourceId");

            // create checker user
            String checker = Utils.uniqueRandomStringGenerator("user", 8);
            UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, checker, "A1b2c3d4e5f$", "resourceId");

            RequestSpecification makerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build()
                    .header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(maker, "A1b2c3d4e5f$"));

            // maker creates datatable with maker-checker enabled, this creates the physical table but queues for
            // approval
            DatatableHelper makerDatatableHelper = new DatatableHelper(makerRequestSpec, this.responseSpec);
            String datatableJson = DatatableHelper.getTestDatatableAsJSON(apptableName, false);
            String datatableName = com.google.gson.JsonParser.parseString(datatableJson).getAsJsonObject().get("datatableName")
                    .getAsString();
            makerDatatableHelper.createDatatable(datatableJson, "");

            // find the pending command
            List<AuditData> auditDetails = makercheckersHelper
                    .getMakerCheckerList(Map.of("actionName", "CREATE", "entityName", "DATATABLE", "makerId", makerUserId));
            assertEquals(1, auditDetails.size(), "Error: Expected only one pending CREATE DATATABLE command");
            Long commandId = auditDetails.get(0).getId();

            // checker rejects the command which should drop the orphaned table
            new FeignMakerCheckerHelper(FineractFeignClientHelper.createNewFineractFeignClient(checker, "A1b2c3d4e5f$"))
                    .rejectMakerCheckerEntry(commandId);

            // verify the datatable no longer exists by trying to create it again
            // verify without maker checker, so transaction rollback in postgres doesn't break the test
            putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_DATATABLE", false);
            rolesHelper.updatePermissions(putPermissionsRequest);

            DatatableHelper adminDatatableHelper = new DatatableHelper(this.requestSpec, this.responseSpec);
            String recreatedName = adminDatatableHelper.createDatatable(datatableJson, "resourceIdentifier");
            assertEquals(datatableName, recreatedName, "Error: Was not able to recreate datatable after rejection cleanup");

            // cleanup after test
            adminDatatableHelper.deleteDatatable(datatableName);
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(false));
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(true));

            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_DATATABLE", false);
            rolesHelper.updatePermissions(putPermissionsRequest);
        }
    }

    @Test
    public void testMakerCheckerUsernameFilter() {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(true));
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(false));

        try {
            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_CLIENT", true);
            rolesHelper.updatePermissions(putPermissionsRequest);

            Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
            Map<String, Boolean> permissionMap = Map.of("CREATE_CLIENT", true, "CREATE_CLIENT_CHECKER", true, "ACTIVATE_CLIENT", true);
            RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, permissionMap);
            final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);

            String maker1 = Utils.uniqueRandomStringGenerator("user", 8);
            String maker2 = Utils.uniqueRandomStringGenerator("user", 8);
            UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, maker1, "A1b2c3d4e5f$", "resourceId");
            UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, maker2, "A1b2c3d4e5f$", "resourceId");

            RequestSpecification maker1RequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build()
                    .header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(maker1, "A1b2c3d4e5f$"));
            RequestSpecification maker2RequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build()
                    .header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(maker2, "A1b2c3d4e5f$"));

            ClientHelper.createClient(maker1RequestSpec, this.responseSpec);
            ClientHelper.createClient(maker2RequestSpec, this.responseSpec);

            List<AuditData> maker1Results = makercheckersHelper
                    .getMakerCheckerList(Map.of("username", maker1, "actionName", "CREATE", "entityName", "CLIENT"));
            assertEquals(1, maker1Results.size(), "Username filter should return only maker1's commands");
            assertEquals(maker1, maker1Results.get(0).getMaker());

            List<AuditData> maker2Results = makercheckersHelper
                    .getMakerCheckerList(Map.of("username", maker2, "actionName", "CREATE", "entityName", "CLIENT"));
            assertEquals(1, maker2Results.size(), "Username filter should return only maker2's commands");
            assertEquals(maker2, maker2Results.get(0).getMaker());

            List<AuditData> noResults = makercheckersHelper.getMakerCheckerList(Map.of("username", "nonexistentuserxyz_999"));
            assertEquals(0, noResults.size(), "Unknown username should return no results");
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(false));
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(true));
            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_CLIENT", false);
            rolesHelper.updatePermissions(putPermissionsRequest);
        }
    }

    @Test
    public void testMakerCheckerDateFilterWithDayMonthYearFormat() {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(true));
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                new PutGlobalConfigurationsRequest().enabled(false));

        try {
            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_CLIENT", true);
            rolesHelper.updatePermissions(putPermissionsRequest);

            Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
            Map<String, Boolean> permissionMap = Map.of("CREATE_CLIENT", true, "CREATE_CLIENT_CHECKER", true, "ACTIVATE_CLIENT", true);
            RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, permissionMap);
            final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);

            String maker = Utils.uniqueRandomStringGenerator("user", 8);
            final Integer makerUserId = (Integer) UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, maker,
                    "A1b2c3d4e5f$", "resourceId");
            RequestSpecification makerRequestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build()
                    .header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(maker, "A1b2c3d4e5f$"));

            ClientHelper.createClient(makerRequestSpec, this.responseSpec);

            // "dd MMMM yyyy" format without dateFormat/locale — previously caused 500 error
            List<AuditData> fromOnly = makercheckersHelper
                    .getMakerCheckerList(Map.of("makerId", makerUserId, "makerDateTimeFrom", "01 January 2020"));
            assertEquals(1, fromOnly.size(), "'dd MMMM yyyy' from-date filter should include today's pending command");

            List<AuditData> fromAndTo = makercheckersHelper.getMakerCheckerList(
                    Map.of("makerId", makerUserId, "makerDateTimeFrom", "01 January 2020", "makerDateTimeTo", "31 December 2030"));
            assertEquals(1, fromAndTo.size(), "'dd MMMM yyyy' date range filter should include today's pending command");

            List<AuditData> pastRange = makercheckersHelper.getMakerCheckerList(
                    Map.of("makerId", makerUserId, "makerDateTimeFrom", "01 January 2020", "makerDateTimeTo", "31 December 2020"));
            assertEquals(0, pastRange.size(), "Past date range should exclude today's pending command");
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(false));
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_SAME_MAKER_CHECKER,
                    new PutGlobalConfigurationsRequest().enabled(true));
            PutPermissionsRequest putPermissionsRequest = new PutPermissionsRequest().putPermissionsItem("CREATE_CLIENT", false);
            rolesHelper.updatePermissions(putPermissionsRequest);
        }
    }

    private Integer createSavingsProductDailyPosting() {
        final String savingsProductJSON = this.savingsProductHelper.withInterestCompoundingPeriodTypeAsDaily()
                .withInterestPostingPeriodTypeAsDaily().withInterestCalculationPeriodTypeAsDailyBalance().build();
        return SavingsProductHelper.createSavingsProduct(savingsProductJSON, requestSpec, responseSpec);
    }

    private Integer createApproveActivateSavingsAccountDailyPosting(final Integer clientID, final String startDate) {
        final Integer savingsProductID = createSavingsProductDailyPosting();
        assertNotNull(savingsProductID);
        return savingsAccountHelper.createApproveActivateSavingsAccount(clientID, savingsProductID, startDate);
    }
}
