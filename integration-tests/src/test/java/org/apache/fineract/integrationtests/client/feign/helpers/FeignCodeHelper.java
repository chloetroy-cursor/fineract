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
import java.util.Optional;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.DeleteCodeValueDataResponse;
import org.apache.fineract.client.models.DeleteCodesResponse;
import org.apache.fineract.client.models.GetCodeValuesDataResponse;
import org.apache.fineract.client.models.GetCodesResponse;
import org.apache.fineract.client.models.PostCodeValueDataResponse;
import org.apache.fineract.client.models.PostCodeValuesDataRequest;
import org.apache.fineract.client.models.PostCodesRequest;
import org.apache.fineract.client.models.PutCodeValueDataResponse;
import org.apache.fineract.client.models.PutCodeValuesDataRequest;
import org.apache.fineract.client.models.PutCodesRequest;
import org.apache.fineract.client.models.PutCodesResponse;
import org.apache.fineract.integrationtests.common.Utils;

public class FeignCodeHelper {

    private static final String CHARGE_OFF_REASONS_CODE_NAME = "ChargeOffReasons";
    private static final String WRITE_OFF_REASONS_CODE_NAME = "WriteOffReasons";

    private final FineractFeignClient fineractClient;

    public FeignCodeHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public Long createChargeOffCodeValue(String value, Integer position) {
        GetCodesResponse code = ok(() -> fineractClient.codes().retrieveOneCodeByName(CHARGE_OFF_REASONS_CODE_NAME));
        PostCodeValueDataResponse response = ok(() -> fineractClient.codeValues().createCodeValue(code.getId(),
                new PostCodeValuesDataRequest().name(value).position(position).description(value).isActive(true)));
        return response.getSubResourceId();
    }

    public Long createWriteOffCodeValue(String value, Integer position) {
        GetCodesResponse code = ok(() -> fineractClient.codes().retrieveOneCodeByName(WRITE_OFF_REASONS_CODE_NAME));
        PostCodeValueDataResponse response = ok(() -> fineractClient.codeValues().createCodeValue(code.getId(),
                new PostCodeValuesDataRequest().name(value).position(position).description(value).isActive(true)));
        return response.getSubResourceId();
    }

    public List<GetCodesResponse> retrieveAllCodes() {
        return ok(() -> fineractClient.codes().retrieveAllCodes());
    }

    public GetCodesResponse retrieveCode(Long codeId) {
        return ok(() -> fineractClient.codes().retrieveOneCode(codeId));
    }

    public CallFailedRuntimeException retrieveCodeExpectingError(Long codeId) {
        return fail(() -> fineractClient.codes().retrieveOneCode(codeId));
    }

    public GetCodesResponse retrieveCodeByName(String codeName) {
        return ok(() -> fineractClient.codes().retrieveOneCodeByName(codeName));
    }

    /**
     * Looks the code up in the full listing instead of {@code GET /codes/name/{name}}, which answers 404 for an unknown
     * name. Use this for "fetch or create" flows where the code may legitimately not exist yet.
     */
    public Optional<GetCodesResponse> findCodeByName(String codeName) {
        return retrieveAllCodes().stream().filter(code -> codeName.equals(code.getName())).findFirst();
    }

    public GetCodesResponse retrieveAnySystemDefinedCode() {
        return retrieveAllCodes().stream().filter(code -> Boolean.TRUE.equals(code.getSystemDefined())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No system defined code found"));
    }

    public Long createCode(String codeName) {
        return ok(() -> fineractClient.codes().createCode(new PostCodesRequest().name(codeName))).getResourceId();
    }

    public CallFailedRuntimeException createCodeExpectingError(String codeName) {
        return fail(() -> fineractClient.codes().createCode(new PostCodesRequest().name(codeName)));
    }

    public PutCodesResponse updateCode(Long codeId, String codeName) {
        return ok(() -> fineractClient.codes().updateCode(codeId, new PutCodesRequest().name(codeName)));
    }

    public CallFailedRuntimeException updateCodeExpectingError(Long codeId, String codeName) {
        return fail(() -> fineractClient.codes().updateCode(codeId, new PutCodesRequest().name(codeName)));
    }

    public DeleteCodesResponse deleteCode(Long codeId) {
        return ok(() -> fineractClient.codes().deleteCode(codeId));
    }

    public CallFailedRuntimeException deleteCodeExpectingError(Long codeId) {
        return fail(() -> fineractClient.codes().deleteCode(codeId));
    }

    /** Adds a value to the named code; like the legacy {@code CodeHelper.createCodeValue}, it sets no description. */
    public Long createCodeValue(String codeName, String value, Integer position) {
        return createCodeValue(retrieveCodeByName(codeName).getId(), value, position);
    }

    public Long createCodeValue(Long codeId, String value, Integer position) {
        return createCodeValue(codeId, new PostCodeValuesDataRequest().name(value).position(position)).getSubResourceId();
    }

    public PostCodeValueDataResponse createCodeValue(Long codeId, PostCodeValuesDataRequest request) {
        return ok(() -> fineractClient.codeValues().createCodeValue(codeId, request));
    }

    public List<GetCodeValuesDataResponse> retrieveAllCodeValues(Long codeId) {
        return ok(() -> fineractClient.codeValues().retrieveAllCodeValues(codeId));
    }

    public GetCodeValuesDataResponse retrieveCodeValue(Long codeId, Long codeValueId) {
        return ok(() -> fineractClient.codeValues().retrieveCodeValue(codeValueId, codeId));
    }

    public CallFailedRuntimeException retrieveCodeValueExpectingError(Long codeId, Long codeValueId) {
        return fail(() -> fineractClient.codeValues().retrieveCodeValue(codeValueId, codeId));
    }

    public PutCodeValueDataResponse updateCodeValue(Long codeId, Long codeValueId, PutCodeValuesDataRequest request) {
        return ok(() -> fineractClient.codeValues().updateCodeValue(codeId, codeValueId, request));
    }

    public DeleteCodeValueDataResponse deleteCodeValue(Long codeId, Long codeValueId) {
        return ok(() -> fineractClient.codeValues().deleteCodeValue(codeId, codeValueId));
    }

    /**
     * Returns the id of the first code value of the named code, creating one when the code has none yet. System codes
     * such as {@code LoanRescheduleReason} ship without values, so a test that needs a reason id has to seed one.
     */
    public Long retrieveOrCreateCodeValueId(String codeName) {
        return retrieveOrCreateCodeValue(retrieveCodeByName(codeName).getId()).getId();
    }

    /** First code value of the code, seeded with a random three-letter name when the code has none. */
    public GetCodeValuesDataResponse retrieveOrCreateCodeValue(Long codeId) {
        List<GetCodeValuesDataResponse> codeValues = retrieveAllCodeValues(codeId);
        if (!codeValues.isEmpty()) {
            return codeValues.get(0);
        }
        String value = Utils.randomStringGenerator("", 3);
        Long codeValueId = createCodeValue(codeId,
                new PostCodeValuesDataRequest().name(value).position(0).description(value).isActive(true)).getSubResourceId();
        return retrieveCodeValue(codeId, codeValueId);
    }
}
