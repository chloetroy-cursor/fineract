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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.ChangePwdUsersUserIdRequest;
import org.apache.fineract.client.models.ChangePwdUsersUserIdResponse;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.GetUsersUserIdResponse;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PostUsersResponse;
import org.apache.fineract.client.models.PutUsersUserIdRequest;
import org.apache.fineract.client.models.PutUsersUserIdResponse;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignUserHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.useradministration.service.AppUserConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserAdministrationTest extends IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(UserAdministrationTest.class);
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private List<Long> transientUsers = new ArrayList<>();

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @AfterEach
    public void tearDown() {
        for (Long userId : this.transientUsers) {
            FeignUserHelper.deleteUser(userId);
        }
        this.transientUsers.clear();
    }

    /** The first entry of the {@code errors} array in a failed call's response body. */
    private static JsonObject firstError(String responseBody) {
        return JsonParser.parseString(responseBody).getAsJsonObject().getAsJsonArray("errors").get(0).getAsJsonObject();
    }

    @Test
    public void testCreateNewUserBlocksDuplicateUsername() {

        final Integer roleId = RolesHelper.createRole(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(roleId);

        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(staffId);

        final Long userId = FeignUserHelper.createUser(roleId.longValue(), staffId.longValue(), "alphabet").getResourceId();
        Assertions.assertNotNull(userId);
        this.transientUsers.add(userId);

        final var failure = FeignUserHelper.createUserExpectingError(roleId.longValue(), staffId.longValue(), "alphabet");
        Assertions.assertEquals(403, failure.getStatus());
        JsonObject reason = firstError(failure.getResponseBody());
        LOG.info("Reason: {}", reason.get("defaultUserMessage"));
        LOG.info("Code: {}", reason.get("userMessageGlobalisationCode"));
        Assertions.assertEquals("User with username alphabet already exists.", reason.get("defaultUserMessage").getAsString());
        Assertions.assertEquals("error.msg.user.duplicate.username", reason.get("userMessageGlobalisationCode").getAsString());
    }

    @Test
    public void testUpdateUserAcceptsNewOrSameUsername() {
        final Integer roleId = RolesHelper.createRole(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(roleId);

        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(staffId);

        final Long userId = FeignUserHelper.createUser(roleId.longValue(), staffId.longValue(), "alphabet").getResourceId();
        Assertions.assertNotNull(userId);
        this.transientUsers.add(userId);

        final Long userId2 = FeignUserHelper.updateUser(userId, "renegade").getResourceId();
        Assertions.assertNotNull(userId2);

        final Long userId3 = FeignUserHelper.updateUser(userId, "renegade").getResourceId();
        Assertions.assertNotNull(userId3);
    }

    @Test
    public void testUpdateUserBlockDuplicateUsername() {
        final Integer roleId = RolesHelper.createRole(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(roleId);

        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(staffId);

        final Long userId = FeignUserHelper.createUser(roleId.longValue(), staffId.longValue(), "alphabet").getResourceId();
        Assertions.assertNotNull(userId);
        this.transientUsers.add(userId);

        final Long userId2 = FeignUserHelper.createUser(roleId.longValue(), staffId.longValue(), "bilingual").getResourceId();
        Assertions.assertNotNull(userId2);
        this.transientUsers.add(userId2);

        final var failure = FeignUserHelper.updateUserExpectingError(userId2, "alphabet");
        Assertions.assertEquals(403, failure.getStatus());
        JsonObject reason = firstError(failure.getResponseBody());
        Assertions.assertEquals("User with username alphabet already exists.", reason.get("defaultUserMessage").getAsString());
        Assertions.assertEquals("error.msg.user.duplicate.username", reason.get("userMessageGlobalisationCode").getAsString());
    }

    @Test
    public void testModifySystemUser() {
        final Long userId = FeignUserHelper.getUserId(AppUserConstants.SYSTEM_USER_NAME);
        Assertions.assertNotNull(userId);

        final var failure = FeignUserHelper.updateUserExpectingError(userId, "systemtest");
        Assertions.assertEquals(403, failure.getStatus());
    }

    @Test
    public void testApplicationUserCanUpdateOwnPassword() {
        // Admin creates a new user with an empty role
        Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
        String originalPassword = "QwE!5rTy#9uP0";
        String simpleUsername = Utils.uniqueRandomStringGenerator("NotificationUser", 4);
        GetOfficesResponse headOffice = OfficeHelper.getHeadOffice();
        PostUsersRequest createUserRequest = new PostUsersRequest().username(simpleUsername).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email("whatever@mifos.org").password(originalPassword)
                .repeatPassword(originalPassword).sendPasswordToEmail(false).officeId(headOffice.getId())
                .roles(List.of(Long.valueOf(roleId)));

        PostUsersResponse userCreationResponse = FeignUserHelper.createUser(createUserRequest);
        Long userId = userCreationResponse.getResourceId();
        Assertions.assertNotNull(userId);

        // User updates its own password
        String updatedPassword = "QwE!5rTy#9uP0u";
        PutUsersUserIdResponse putUsersUserIdResponse = ok(newFineractClient(simpleUsername, originalPassword).users.updateUser(userId,
                new PutUsersUserIdRequest().password(updatedPassword).repeatPassword(updatedPassword)));
        Assertions.assertNotNull(putUsersUserIdResponse.getResourceId());

        // From then on the originalPassword is not working anymore
        CallFailedRuntimeException callFailedRuntimeException = Assertions.assertThrows(CallFailedRuntimeException.class, () -> {
            ok(newFineractClient(simpleUsername, originalPassword).users.retrieveOneUser(userId));
        });
        Assertions.assertEquals(401, callFailedRuntimeException.getResponse().raw().code());
        Assertions.assertTrue(callFailedRuntimeException.getMessage().contains("Unauthorized"));

        // The update password is still working perfectly
        GetUsersUserIdResponse ok = ok(newFineractClient(simpleUsername, updatedPassword).users.retrieveOneUser(userId));
    }

    @Test
    public void testApplicationUserCanChangeOwnPassword() {
        // Admin creates a new user with an empty role
        Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
        String originalPassword = "QwE!5rTy#9uP0";
        String simpleUsername = Utils.uniqueRandomStringGenerator("NotificationUser", 4);
        GetOfficesResponse headOffice = OfficeHelper.getHeadOffice();
        PostUsersRequest createUserRequest = new PostUsersRequest().username(simpleUsername).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email("whatever@mifos.org").password(originalPassword)
                .repeatPassword(originalPassword).sendPasswordToEmail(false).officeId(headOffice.getId())
                .roles(List.of(Long.valueOf(roleId)));

        PostUsersResponse userCreationResponse = FeignUserHelper.createUser(createUserRequest);
        Long userId = userCreationResponse.getResourceId();
        Assertions.assertNotNull(userId);

        // User changes its own password

        String updatedPassword = "pX268-4Pfv|kF6";
        ChangePwdUsersUserIdResponse changePwdUsersUserIdResponse = ok(newFineractClient(simpleUsername, originalPassword).users
                .changePasswordUser(userId, new ChangePwdUsersUserIdRequest().password(updatedPassword).repeatPassword(updatedPassword)));
        Assertions.assertNotNull(changePwdUsersUserIdResponse.getResourceId());

        // From then on the originalPassword is not working anymore
        CallFailedRuntimeException callFailedRuntimeException = Assertions.assertThrows(CallFailedRuntimeException.class, () -> {
            ok(newFineractClient(simpleUsername, originalPassword).users.retrieveOneUser(userId));
        });
        Assertions.assertEquals(401, callFailedRuntimeException.getResponse().raw().code());
        Assertions.assertTrue(callFailedRuntimeException.getMessage().contains("Unauthorized"));

        // The update password is still working perfectly
        GetUsersUserIdResponse ok = ok(newFineractClient(simpleUsername, updatedPassword).users.retrieveOneUser(userId));
    }

    @Test
    public void testApplicationUserShallNotBeAbleToChangeItsOwnRoles() {
        // Admin creates a new user with one role assigned
        Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
        String password = "QwE!5rTy#9uP0";
        String simpleUsername = Utils.uniqueRandomStringGenerator("NotificationUser", 4);
        GetOfficesResponse headOffice = OfficeHelper.getHeadOffice();
        PostUsersRequest createUserRequest = new PostUsersRequest().username(simpleUsername).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email("whatever@mifos.org").password(password).repeatPassword(password)
                .sendPasswordToEmail(false).officeId(headOffice.getId()).roles(List.of(Long.valueOf(roleId)));

        PostUsersResponse userCreationResponse = FeignUserHelper.createUser(createUserRequest);
        Long userId = userCreationResponse.getResourceId();
        Assertions.assertNotNull(userId);

        // Admin creates a second role
        Integer roleId2 = RolesHelper.createRole(requestSpec, responseSpec);

        // User tries to update it's own roles
        CallFailedRuntimeException callFailedRuntimeException = Assertions.assertThrows(CallFailedRuntimeException.class, () -> {
            ok(newFineractClient(simpleUsername, password).users.updateUser(userId,
                    new PutUsersUserIdRequest().roles(List.of(Long.valueOf(roleId2)))));
        });

        Assertions.assertEquals(400, callFailedRuntimeException.getResponse().raw().code());
        Assertions.assertTrue(callFailedRuntimeException.getMessage().contains("not.enough.permission.to.update.fields"));
    }

    @Test
    public void testUserCreationWithValidPassword() {
        String validPassword = "Abcdef1#2$3%XYZ";

        PostUsersRequest createUserRequest = FeignUserHelper.buildUserRequest(validPassword);
        PostUsersResponse userCreationResponse = FeignUserHelper.createUser(createUserRequest);

        Assertions.assertNotNull(userCreationResponse.getResourceId());
    }

    @Test
    public void testUserCreationWithInvalidPasswords() {
        Map<String, String> invalidPasswords = Map.ofEntries(Map.entry("TooShort", "Ab1#Xyz"), // Less than 12
                                                                                               // characters
                Map.entry("NoUppercase", "abcdefg1#2$3%xyz"), // Missing uppercase letter
                Map.entry("NoLowercase", "ABCDEFG1#2$3%XYZ"), // Missing lowercase letter
                Map.entry("NoDigit", "Abcdefg#@$%XYZabc"), // Missing digit
                Map.entry("NoSpecialChar", "Abcdefg123456XYZ"), // Missing special character
                Map.entry("ContainsWhitespace", "Abcdefg1# 2$3%"), // Contains whitespace
                Map.entry("RepeatedCharacters", "AAbbcc11##$$%%YY") // Contains repeated characters
        );

        invalidPasswords.forEach((description, password) -> {
            PostUsersRequest createUserRequest = FeignUserHelper.buildUserRequest(password);
            final var failure = FeignUserHelper.createUserExpectingError(createUserRequest);
            Assertions.assertEquals(400, failure.getStatus(), "Expected HTTP 400 for: " + description);
            JsonObject jsonResponse = JsonParser.parseString(failure.getResponseBody()).getAsJsonObject();
            Assertions.assertEquals("validation.msg.validation.errors.exist",
                    jsonResponse.get("userMessageGlobalisationCode").getAsString(), "Expected user message code for: " + description);

            JsonObject errorDetails = firstError(failure.getResponseBody());
            Assertions.assertEquals("password", errorDetails.get("parameterName").getAsString(),
                    "Expected validation error parameter name for: " + description);
            Assertions.assertEquals("validation.msg.user.password.does.not.match.regexp",
                    errorDetails.get("userMessageGlobalisationCode").getAsString(), "Expected validation code for: " + description);
        });
    }
}
