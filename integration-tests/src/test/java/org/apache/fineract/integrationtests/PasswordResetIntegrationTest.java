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

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PostUsersResponse;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignAuthenticationHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignRawHttpHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignRawHttpHelper.RawResponse;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PasswordResetIntegrationTest extends IntegrationTest {

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private GlobalConfigurationHelper globalConfigurationHelper;
    private List<Integer> transientUsers = new ArrayList<>();

    @BeforeEach
    public void setup() {
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + FeignAuthenticationHelper.base64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.globalConfigurationHelper = new GlobalConfigurationHelper();
    }

    @AfterEach
    public void tearDown() {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.FORCE_PASSWORD_RESET_ON_FIRST_LOGIN,
                new PutGlobalConfigurationsRequest().value(0L).enabled(false));

        for (Integer userId : this.transientUsers) {
            UserHelper.deleteUser(this.requestSpec, this.responseSpec, userId);
        }
        this.transientUsers.clear();
    }

    @Test
    public void testPasswordResetEnforcement() {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.FORCE_PASSWORD_RESET_ON_FIRST_LOGIN,
                new PutGlobalConfigurationsRequest().value(0L).enabled(true));

        String password = "Abcdef1#2$3%XYZ";
        PostUsersRequest userRequest = UserHelper.buildUserRequest(responseSpec, requestSpec, password);
        PostUsersResponse userResponse = UserHelper.createUser(requestSpec, responseSpec, userRequest);
        Long userId = userResponse.getResourceId();
        assertNotNull(userId, "User creation failed to return an ID!");
        this.transientUsers.add(userId.intValue());
        String username = userRequest.getUsername();

        RawResponse loginResponse = attemptLogin(username, password);
        assertEquals(403, loginResponse.status(), "User should be forced to change password");

        String newPassword = "Abcdef1#2$3%XYZ_NEW";
        RawResponse changePasswordResponse = changePassword(username, password, userId, newPassword);
        assertEquals(200, changePasswordResponse.status(), "Password change should succeed");

        loginResponse = attemptLogin(username, newPassword);
        assertEquals(200, loginResponse.status(), "User should be able to login after reset");
    }

    @Test
    public void testFeatureDisabledByDefault() {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.FORCE_PASSWORD_RESET_ON_FIRST_LOGIN,
                new PutGlobalConfigurationsRequest().value(0L).enabled(false));

        String password = "Abcdef1#2$3%XYZ";
        PostUsersRequest userRequest = UserHelper.buildUserRequest(responseSpec, requestSpec, password);
        PostUsersResponse userResponse = UserHelper.createUser(requestSpec, responseSpec, userRequest);
        assertNotNull(userResponse.getResourceId(), "User creation failed!");
        this.transientUsers.add(userResponse.getResourceId().intValue());
        String username = userRequest.getUsername();

        RawResponse loginResponse = attemptLogin(username, password);
        assertEquals(200, loginResponse.status(), "User should login normally when feature is disabled");
    }

    private RawResponse attemptLogin(String username, String password) {
        return FeignRawHttpHelper.anonymous().response("POST", "/fineract-provider/api/v1/authentication?" + Utils.TENANT_IDENTIFIER,
                "{\"username\":\"" + username + "\", \"password\":\"" + password + "\"}");
    }

    private RawResponse changePassword(String username, String password, Long userId, String newPassword) {
        return FeignRawHttpHelper.callAcceptingAnyStatus(FeignRawHttpHelper.basicAuthorization(username, password)).response("POST",
                "/fineract-provider/api/v1/users/" + userId + "/pwd?" + Utils.TENANT_IDENTIFIER,
                "{\"password\":\"" + newPassword + "\", \"repeatPassword\":\"" + newPassword + "\"}");
    }
}
