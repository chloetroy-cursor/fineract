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
package org.apache.fineract.integrationtests.common.organisation;

import static org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper.ACTIVATE_COMMAND;
import static org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper.CLOSE_COMMAND;
import static org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper.DIRECT_TRIGGER_TYPE;
import static org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper.REACTIVATE_COMMAND;
import static org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper.SCHEDULED_TRIGGER_TYPE;
import static org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper.TRIGGERED_TRIGGER_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.time.format.DateTimeFormatter;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignSmsCampaignHelper;
import org.apache.fineract.integrationtests.client.feign.modules.FeignErrors;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = { 9191 })
public class CampaignsTest {

    private static final String NON_TRIGGERED_REPORT_NAME = "Prospective Clients";
    private static final String TRIGGERED_REPORT_NAME = "Client Activated";

    public static final String DATE_FORMAT = "dd MMMM yyyy";

    private final ClientAndServer client;
    private final FeignSmsCampaignHelper campaignsHelper = new FeignSmsCampaignHelper(FineractFeignClientHelper.getFineractFeignClient());

    public CampaignsTest(ClientAndServer client) {
        this.client = client;
    }

    @BeforeEach
    public void setup() {
        // Set up mock server for message-gateway
        this.client.when(request().withMethod("GET").withPath("/smsbridges"))
                .respond(response().withContentType(MediaType.APPLICATION_JSON).withBody("[\n" //
                        + "    {\n" //
                        + "        \"id\": 1,\n" //
                        + "        \"tenantId\": 1,\n" //
                        + "        \"phoneNo\": \"+1234567890\",\n" //
                        + "        \"providerName\": \"Dummy SMS Provider - Testing\",\n" //
                        + "        \"providerDescription\": \"Dummy, just for testing\"\n" //
                        + "     }\n" //
                        + "]") //
                );
    }

    @Test
    public void testSupportedActionsForCampaignWithTriggerTypeAsDirect() {
        BusinessDateHelper.runAt(DateTimeFormatter.ofPattern(DATE_FORMAT).format(Utils.getLocalDateOfTenant()), () -> {
            runCampaignLifecycle(NON_TRIGGERED_REPORT_NAME, DIRECT_TRIGGER_TYPE);
        });
    }

    @Test
    public void testSupportedActionsForCampaignWithTriggerTypeAsScheduled() {
        BusinessDateHelper.runAt(DateTimeFormatter.ofPattern(DATE_FORMAT).format(Utils.getLocalDateOfTenant()), () -> {
            runCampaignLifecycle(NON_TRIGGERED_REPORT_NAME, SCHEDULED_TRIGGER_TYPE);
        });
    }

    @Test
    public void testSupportedActionsForCampaignWithTriggerTypeAsTriggered() {
        BusinessDateHelper.runAt(DateTimeFormatter.ofPattern(DATE_FORMAT).format(Utils.getLocalDateOfTenant()), () -> {
            runCampaignLifecycle(TRIGGERED_REPORT_NAME, TRIGGERED_TRIGGER_TYPE);
        });
    }

    @Test
    public void testSupportedActionsForCampaignWithError() {
        BusinessDateHelper.runAt(DateTimeFormatter.ofPattern(DATE_FORMAT).format(Utils.getLocalDateOfTenant()), () -> {
            // creating new campaign
            Long campaignId = this.campaignsHelper.createCampaign(NON_TRIGGERED_REPORT_NAME, DIRECT_TRIGGER_TYPE);
            assertEquals(campaignId, this.campaignsHelper.getCampaign(campaignId).getId());

            // activating campaign with failure
            CallFailedRuntimeException futureActivation = this.campaignsHelper.performActionExpectingError(campaignId, ACTIVATE_COMMAND,
                    Utils.getLocalDateOfTenant().plusDays(1));
            assertEquals(400, futureActivation.getStatus());
            assertEquals("error.msg.campaign.activationDate.in.the.future", FeignErrors.errorGlobalisationCode(futureActivation));

            // activating campaign
            Long activatedCampaignId = this.campaignsHelper.performAction(campaignId, ACTIVATE_COMMAND);
            assertEquals(activatedCampaignId, campaignId);

            // activating campaign with failure
            CallFailedRuntimeException secondActivation = this.campaignsHelper.performActionExpectingError(activatedCampaignId,
                    ACTIVATE_COMMAND, Utils.getLocalDateOfTenant());
            assertEquals(400, secondActivation.getStatus());
            assertEquals("error.msg.campaign.already.active", FeignErrors.errorGlobalisationCode(secondActivation));

            // closing campaign again for deletion
            Long closedCampaignId = this.campaignsHelper.performAction(campaignId, CLOSE_COMMAND);
            assertEquals(closedCampaignId, campaignId);

            // deleting campaign
            Long deletedCampaignId = this.campaignsHelper.deleteCampaign(campaignId);
            assertEquals(deletedCampaignId, campaignId);
        });
    }

    private void runCampaignLifecycle(String reportName, int triggerType) {
        // creating new campaign
        Long campaignId = this.campaignsHelper.createCampaign(reportName, triggerType);
        assertEquals(campaignId, this.campaignsHelper.getCampaign(campaignId).getId());

        // updating campaign
        Long updatedCampaignId = this.campaignsHelper.updateCampaign(campaignId, reportName, triggerType);
        assertEquals(campaignId, updatedCampaignId);

        // activating campaign
        Long activatedCampaignId = this.campaignsHelper.performAction(campaignId, ACTIVATE_COMMAND);
        assertEquals(activatedCampaignId, campaignId);

        // closing campaign
        Long closedCampaignId = this.campaignsHelper.performAction(campaignId, CLOSE_COMMAND);
        assertEquals(closedCampaignId, campaignId);

        // reactivating campaign
        Long reactivateCampaignId = this.campaignsHelper.performAction(campaignId, REACTIVATE_COMMAND);
        assertEquals(reactivateCampaignId, campaignId);

        // closing campaign again for deletion
        closedCampaignId = this.campaignsHelper.performAction(campaignId, CLOSE_COMMAND);
        assertEquals(closedCampaignId, campaignId);

        // deleting campaign
        Long deletedCampaignId = this.campaignsHelper.deleteCampaign(campaignId);
        assertEquals(deletedCampaignId, campaignId);
    }
}
