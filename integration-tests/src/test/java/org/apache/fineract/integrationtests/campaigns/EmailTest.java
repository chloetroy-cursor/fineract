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
package org.apache.fineract.integrationtests.campaigns;

import static org.apache.fineract.integrationtests.client.IntegrationTest.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignAuthenticationHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignRawHttpHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EmailTest {

    private static final Gson GSON = new Gson();
    private static final String EMAIL_URL = "/fineract-provider/api/v1/email";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    @BeforeEach
    public void setup() {
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + FeignAuthenticationHelper.base64EncodedAuthenticationKey());
        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @Test
    public void testEmailCreateRetrieveUpdateDeleteLifecycle() {
        // Arrange: client must have an emailAddress, since EmailMessageAssembler
        // derives the recipient address from it (no address => data integrity exception).
        PostClientsRequest clientRequest = ClientHelper.defaultClientCreationRequest();
        clientRequest.emailAddress(Utils.randomStringGenerator("email_", 6) + "@example.com");
        PostClientsResponse client = ClientHelper.createClient(clientRequest);

        String initialSubject = Utils.randomStringGenerator("Subject_", 10);
        String initialMessage = Utils.randomStringGenerator("Message_", 20);

        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("clientId", client.getClientId());
        createRequest.put("emailSubject", initialSubject);
        createRequest.put("emailMessage", initialMessage);
        createRequest.put("locale", "en");

        // Act: CREATE
        Long emailId = ((Number) FeignRawHttpHelper.post(emailUrl(), GSON.toJson(createRequest), "resourceId")).longValue();

        // Assert: RETRIEVE after create
        JsonObject created = retrieveEmail(emailId);
        assertThat(created.get("id").getAsLong()).isEqualTo(emailId);
        assertThat(created.get("clientId").getAsLong()).isEqualTo(client.getClientId().longValue());
        assertThat(created.get("emailSubject").getAsString()).isEqualTo(initialSubject);
        assertThat(created.get("emailMessage").getAsString()).isEqualTo(initialMessage);

        // Act: UPDATE (only emailMessage is a supported update param)
        String updatedMessage = Utils.randomStringGenerator("UpdatedMessage_", 20);
        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("emailMessage", updatedMessage);

        JsonObject updateResponse = JsonParser.parseString(FeignRawHttpHelper.put(emailUrl(emailId), GSON.toJson(updateRequest)))
                .getAsJsonObject();
        assertThat(updateResponse.get("resourceId").getAsLong()).isEqualTo(emailId);
        assertThat(updateResponse.getAsJsonObject("changes").get("emailMessage").getAsString()).isEqualTo(updatedMessage);

        // Assert: RETRIEVE after update
        JsonObject updated = retrieveEmail(emailId);
        assertThat(updated.get("emailMessage").getAsString()).isEqualTo(updatedMessage);
        // subject is untouched by update, since UPDATE_REQUEST_DATA_PARAMETERS only allows emailMessage
        assertThat(updated.get("emailSubject").getAsString()).isEqualTo(initialSubject);

        // Act: DELETE
        Long deletedResourceId = ((Number) FeignRawHttpHelper.delete(emailUrl(emailId), "resourceId")).longValue();
        assertThat(deletedResourceId).isEqualTo(emailId);

        // Assert: RETRIEVE after delete should 404
        FeignRawHttpHelper.call(404).get(emailUrl(emailId));
    }

    @Test
    public void testEmailCreateWithStaffIdOnlyDoesNotThrow() {
        // Arrange: staff must have an emailAddress, since EmailMessageAssembler
        // derives the recipient address from it (no address => data integrity exception,
        // which the platform maps to a 403 -- Postgres enforces the NOT NULL constraint
        // on email_address strictly, unlike MySQL/MariaDB in non-strict mode).
        Map<String, Object> staffRequest = StaffHelper.getMapWithJoiningDate();
        staffRequest.put("officeId", 1);
        staffRequest.put("firstname", Utils.uniqueRandomStringGenerator("staff_", 5));
        staffRequest.put("lastname", Utils.uniqueRandomStringGenerator("Doe_", 4));
        staffRequest.put("isLoanOfficer", true);
        staffRequest.put("emailAddress", Utils.randomStringGenerator("staff_email_", 6) + "@example.com");

        Integer staffId = (Integer) StaffHelper.createStaffWithJson(requestSpec, responseSpec, GSON.toJson(staffRequest)).get("resourceId");

        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("staffId", staffId);
        createRequest.put("emailSubject", Utils.randomStringGenerator("Subject_", 10));
        createRequest.put("emailMessage", Utils.randomStringGenerator("Message_", 20));
        createRequest.put("locale", "en");

        Long emailId = ((Number) FeignRawHttpHelper.post(emailUrl(), GSON.toJson(createRequest), "resourceId")).longValue();

        assertThat(retrieveEmail(emailId).get("staffId").getAsLong()).isEqualTo(staffId.longValue());
    }

    @Test
    public void testEmailCreateWithoutClientOrStaffIdFails() {
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("emailSubject", Utils.randomStringGenerator("Subject_", 10));
        createRequest.put("emailMessage", Utils.randomStringGenerator("Message_", 20));
        createRequest.put("locale", "en");

        FeignRawHttpHelper.call(400).post(emailUrl(), GSON.toJson(createRequest));
    }

    private JsonObject retrieveEmail(final Long emailId) {
        return JsonParser.parseString(FeignRawHttpHelper.get(emailUrl(emailId))).getAsJsonObject();
    }

    private String emailUrl() {
        return EMAIL_URL + "?" + Utils.TENANT_IDENTIFIER;
    }

    private String emailUrl(final Long emailId) {
        return EMAIL_URL + "/" + emailId + "?" + Utils.TENANT_IDENTIFIER;
    }
}
