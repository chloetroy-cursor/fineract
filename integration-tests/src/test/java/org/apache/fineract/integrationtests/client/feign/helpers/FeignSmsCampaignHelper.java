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

import static org.apache.fineract.client.feign.util.FeignCalls.fail;
import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.SmsBusinessRulesData;
import org.apache.fineract.client.models.SmsCampaignData;
import org.apache.fineract.integrationtests.client.feign.modules.FeignTestConstants;
import org.apache.fineract.integrationtests.common.Utils;

/**
 * Drives {@code /v1/smscampaigns}. Every campaign is an SMS campaign on provider 1 (the mocked message gateway) built
 * from one of the business-rule reports the template advertises; the caller only picks the report and the trigger type.
 */
public class FeignSmsCampaignHelper {

    public static final String ACTIVATE_COMMAND = "activate";
    public static final String CLOSE_COMMAND = "close";
    public static final String REACTIVATE_COMMAND = "reactivate";

    public static final int DIRECT_TRIGGER_TYPE = 1;
    public static final int SCHEDULED_TRIGGER_TYPE = 2;
    public static final int TRIGGERED_TRIGGER_TYPE = 3;

    private static final String DATE_TIME_FORMAT = "dd MMMM yyyy HH:mm:ss";
    private static final int SMS_CAMPAIGN_TYPE = 1;
    private static final long MOCKED_PROVIDER_ID = 1L;

    private final FineractFeignClient fineractClient;
    private final SmsCampaignCommandsApi smsCampaignCommandsApi;

    public FeignSmsCampaignHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
        this.smsCampaignCommandsApi = fineractClient.create(SmsCampaignCommandsApi.class);
    }

    public Long createCampaign(String reportName, int triggerType) {
        return createCampaign(reportName, triggerType, Utils.randomStringGenerator("Campaign_Name_", 5));
    }

    public Long createCampaign(String reportName, int triggerType, String campaignName) {
        return ok(() -> smsCampaignCommandsApi.createCampaign(campaignRequest(reportName, triggerType, campaignName))).getResourceId();
    }

    public CallFailedRuntimeException createCampaignExpectingError(String reportName, int triggerType, String campaignName) {
        return fail(() -> smsCampaignCommandsApi.createCampaign(campaignRequest(reportName, triggerType, campaignName)));
    }

    public SmsCampaignData getCampaign(Long campaignId) {
        return ok(() -> fineractClient.defaultApi().retrieveOneSmsCampaign(campaignId));
    }

    public Long updateCampaign(Long campaignId, String reportName, int triggerType) {
        Map<String, Object> request = campaignRequest(reportName, triggerType, Utils.randomStringGenerator("Campaign_Name_", 5));
        return ok(() -> smsCampaignCommandsApi.updateCampaign(campaignId, request)).getResourceId();
    }

    public Long deleteCampaign(Long campaignId) {
        return ok(() -> smsCampaignCommandsApi.deleteCampaign(campaignId)).getResourceId();
    }

    /** Activates, closes or reactivates the campaign as of the tenant's current date. */
    public Long performAction(Long campaignId, String command) {
        return performAction(campaignId, command, Utils.getLocalDateOfTenant());
    }

    public Long performAction(Long campaignId, String command, LocalDate actionDate) {
        return ok(() -> smsCampaignCommandsApi.handleCommand(campaignId, command, actionRequest(command, actionDate))).getResourceId();
    }

    public CallFailedRuntimeException performActionExpectingError(Long campaignId, String command, LocalDate actionDate) {
        return fail(() -> smsCampaignCommandsApi.handleCommand(campaignId, command, actionRequest(command, actionDate)));
    }

    public List<SmsBusinessRulesData> getReports() {
        return ok(smsCampaignCommandsApi::retrieveTemplate).getBusinessRulesOptions();
    }

    private Long reportId(String reportName) {
        return getReports().stream()//
                .filter(report -> reportName.equals(report.getReportName()))//
                .map(SmsBusinessRulesData::getReportId)//
                .findFirst()//
                .orElseThrow(() -> new AssertionError("No business rule report named '" + reportName + "'"));
    }

    private Map<String, Object> campaignRequest(String reportName, int triggerType, String campaignName) {
        Map<String, Object> request = new HashMap<>();
        request.put("providerId", MOCKED_PROVIDER_ID);
        request.put("triggerType", triggerType);
        if (triggerType == SCHEDULED_TRIGGER_TYPE) {
            request.put("recurrenceStartDate",
                    Utils.getLocalDateTimeOfTenant().plusMinutes(1).format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));
            request.put("frequency", 1);
            request.put("interval", "1");
        }
        request.put("campaignName", campaignName);
        request.put("campaignType", SMS_CAMPAIGN_TYPE);
        request.put("message", "Hi, this is from integration tests runner");
        request.put("locale", FeignTestConstants.LOCALE);
        request.put("dateFormat", FeignTestConstants.DATETIME_PATTERN);
        request.put("dateTimeFormat", DATE_TIME_FORMAT);
        request.put("runReportId", reportId(reportName));
        Map<String, Object> paramValue = new HashMap<>();
        paramValue.put("officeId", "1");
        paramValue.put("loanOfficerId", "1");
        paramValue.put("reportName", reportName);
        request.put("paramValue", paramValue);
        return request;
    }

    private Map<String, Object> actionRequest(String command, LocalDate actionDate) {
        Map<String, Object> request = new HashMap<>();
        String dateField = CLOSE_COMMAND.equalsIgnoreCase(command) ? "closureDate" : "activationDate";
        request.put(dateField, actionDate.format(DateTimeFormatter.ofPattern(FeignTestConstants.DATETIME_PATTERN)));
        request.put("locale", FeignTestConstants.LOCALE);
        request.put("dateFormat", FeignTestConstants.DATETIME_PATTERN);
        return request;
    }
}
