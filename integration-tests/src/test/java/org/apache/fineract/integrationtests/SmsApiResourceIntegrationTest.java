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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignAuthenticationHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignRawHttpHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.organisation.CampaignsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

/**
 * Integration tests for the retrieveAllSmsByStatus endpoint in SmsApiResource. Ensures correct retrieval of SMS
 * messages by campaign and status.
 */
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = { 9191 })
public class SmsApiResourceIntegrationTest {

    private static final String SMS_URL = "/fineract-provider/api/v1/sms";
    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private CampaignsHelper campaignsHelper;
    private final ClientAndServer client;

    public SmsApiResourceIntegrationTest(ClientAndServer client) {
        this.client = client;
        this.client.when(HttpRequest.request().withMethod("GET").withPath("/smsbridges"))
                .respond(HttpResponse.response().withContentType(MediaType.APPLICATION_JSON).withBody(
                        "[{\"id\":1,\"tenantId\":1,\"phoneNo\":\"+1234567890\",\"providerName\":\"Dummy SMS Provider - Testing\",\"providerDescription\":\"Dummy, just for testing\"}]"));
    }

    @BeforeEach
    public void setup() {
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + FeignAuthenticationHelper.base64EncodedAuthenticationKey());
        this.requestSpec.header("Fineract-Platform-TenantId", "default");
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.campaignsHelper = new CampaignsHelper(this.requestSpec, this.responseSpec);
    }

    /**
     * Test retrieving SMS messages by status for a valid campaign.
     */
    @Test
    public void testRetrieveAllSmsByStatus_validStatus() {
        String reportName = "Prospective Clients";
        int triggerType = 1;
        Integer campaignId = campaignsHelper.createCampaign(reportName, triggerType);
        campaignsHelper.verifyCampaignCreatedOnServer(requestSpec, responseSpec, campaignId);
        campaignsHelper.performActionsOnCampaign(requestSpec, responseSpec, campaignId, "activate");

        Integer clientId = ClientHelper.createClientAsPerson(requestSpec, responseSpec);

        String smsJson = String.format(
                "{\"groupId\":null,\"clientId\":%d,\"staffId\":null,\"message\":\"Integration test message\",\"campaignId\":%d}", clientId,
                campaignId);
        assertNotNull(FeignRawHttpHelper.post(SMS_URL, smsJson, "resourceId"));

        List<Map<String, Object>> allSms = FeignRawHttpHelper.get(SMS_URL, "");
        Integer status = null;
        for (Map<String, Object> sms : allSms) {
            Object smsClientId = sms.get("clientId");
            Object smsCampaignName = sms.get("campaignName");
            if (smsClientId != null && smsCampaignName != null && smsClientId.equals(clientId)
                    && smsCampaignName.equals("Campaign_Name_" + Integer.toHexString(campaignId).toUpperCase(Locale.ROOT))) {
                Map<String, Object> statusObj = (Map<String, Object>) sms.get("status");
                if (statusObj != null) {
                    status = ((Number) statusObj.get("id")).intValue();
                    break;
                }
            }
        }
        if (status == null) {
            status = 100;
        }
        int limit = 10;
        Map<String, Object> page = FeignRawHttpHelper.get(messageByStatusUrl(campaignId, status, limit), "");
        List<Map<String, Object>> pageItems = (List<Map<String, Object>>) page.get("pageItems");
        assertNotNull(pageItems);
        assertTrue(pageItems.stream().anyMatch(sms -> clientId.equals(sms.get("clientId"))),
                "pageItems.clientId should contain " + clientId);
    }

    /**
     * Test retrieving SMS messages by status for an invalid status value.
     */
    @Test
    public void testRetrieveAllSmsByStatus_invalidStatus() {
        String reportName = "Prospective Clients";
        int triggerType = 1;
        Integer campaignId = campaignsHelper.createCampaign(reportName, triggerType);
        campaignsHelper.verifyCampaignCreatedOnServer(requestSpec, responseSpec, campaignId);
        campaignsHelper.performActionsOnCampaign(requestSpec, responseSpec, campaignId, "activate");

        int invalidStatus = 9999;
        int limit = 10;
        Map<String, Object> page = FeignRawHttpHelper.get(messageByStatusUrl(campaignId, invalidStatus, limit), "");
        assertNotNull(page.get("pageItems"));
    }

    private static String messageByStatusUrl(Integer campaignId, int status, int limit) {
        return SMS_URL + "/" + campaignId + "/messageByStatus?status=" + status + "&limit=" + limit;
    }
}
