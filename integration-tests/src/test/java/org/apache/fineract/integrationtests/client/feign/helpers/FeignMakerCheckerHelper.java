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

import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.AuditData;
import org.apache.fineract.client.models.CommandProcessingResult;

/**
 * Maker-checker inbox and approvals through the Feign client. Checking is a per-user action, so build one helper per
 * acting user with {@code FineractFeignClientHelper.createNewFineractFeignClient(username, password)}.
 */
public class FeignMakerCheckerHelper {

    private static final String COMMAND_APPROVE = "approve";
    private static final String COMMAND_REJECT = "reject";

    private final FineractFeignClient fineractClient;
    private final MakerCheckerCommandsApi commandsApi;

    public FeignMakerCheckerHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
        this.commandsApi = fineractClient.create(MakerCheckerCommandsApi.class);
    }

    public List<AuditData> getMakerCheckerList() {
        return getMakerCheckerList(Map.of());
    }

    /**
     * Lists the entries the acting user may check, filtered by the {@code GET /makercheckers} query parameters
     * ({@code actionName}, {@code entityName}, {@code makerId}, {@code username}, {@code makerDateTimeFrom}, ...).
     */
    public List<AuditData> getMakerCheckerList(Map<String, Object> queryParams) {
        return ok(() -> fineractClient.makerCheckerOr4EyeFunctionality().retrieveCommands(queryParams));
    }

    public CommandProcessingResult approveMakerCheckerEntry(Long auditId) {
        return ok(() -> commandsApi.checkEntry(auditId, COMMAND_APPROVE));
    }

    public CallFailedRuntimeException approveMakerCheckerEntryExpectingError(Long auditId) {
        return fail(() -> commandsApi.checkEntry(auditId, COMMAND_APPROVE));
    }

    public CommandProcessingResult rejectMakerCheckerEntry(Long auditId) {
        return ok(() -> commandsApi.checkEntry(auditId, COMMAND_REJECT));
    }
}
