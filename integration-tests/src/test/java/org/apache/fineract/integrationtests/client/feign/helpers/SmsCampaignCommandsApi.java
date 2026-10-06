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
package org.apache.fineract.integrationtests.client.feign.helpers;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.util.Map;
import org.apache.fineract.client.models.CommandProcessingResult;
import org.apache.fineract.client.models.SmsCampaignData;

/**
 * Feign interface for the {@code /v1/smscampaigns} operations the generated {@code DefaultApi} cannot drive. The spec
 * declares the create and update bodies as {@code CommandWrapper} (the campaign DTOs are {@code hidden}), gives the
 * command endpoint no body at all so an {@code activationDate} cannot be sent, and leaves {@code template} and
 * {@code delete} without a response schema, which the generator turns into {@code void}. Each call here binds the
 * answer to the generated model instead. Reading one campaign is specced correctly and stays on
 * {@code DefaultApi.retrieveOneSmsCampaign}. Check SmsCampaignApiResource.java for the server-side implementation.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface SmsCampaignCommandsApi {

    @RequestLine("GET /v1/smscampaigns/template")
    SmsCampaignData retrieveTemplate();

    @RequestLine("POST /v1/smscampaigns")
    CommandProcessingResult createCampaign(Map<String, Object> request);

    @RequestLine("PUT /v1/smscampaigns/{campaignId}")
    CommandProcessingResult updateCampaign(@Param("campaignId") Long campaignId, Map<String, Object> request);

    @RequestLine("POST /v1/smscampaigns/{campaignId}?command={command}")
    CommandProcessingResult handleCommand(@Param("campaignId") Long campaignId, @Param("command") String command,
            Map<String, Object> request);

    @RequestLine("DELETE /v1/smscampaigns/{campaignId}")
    CommandProcessingResult deleteCampaign(@Param("campaignId") Long campaignId);
}
