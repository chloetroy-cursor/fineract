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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.DeleteCodeValueDataResponse;
import org.apache.fineract.client.models.GetCodeValuesDataResponse;
import org.apache.fineract.client.models.GetCodesResponse;
import org.apache.fineract.client.models.PostCodeValuesDataRequest;
import org.apache.fineract.client.models.PutCodeValueDataResponse;
import org.apache.fineract.client.models.PutCodeValuesDataRequest;
import org.apache.fineract.client.models.PutCodesResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignCodeHelper;
import org.apache.fineract.integrationtests.client.feign.modules.FeignErrors;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test for creating, updating, deleting codes and code values
 *
 */
public class SystemCodeTest {

    private static final int HTTP_NOT_FOUND = 404;

    private FeignCodeHelper codeHelper;

    @BeforeEach
    public void setup() {
        codeHelper = new FeignCodeHelper(FineractFeignClientHelper.getFineractFeignClient());
    }

    // @Ignore()
    @Test
    // scenario 57, 58, 59, 60
    public void testCreateCode() {
        final String codeName = "Client Marital Status";

        final Long createResponseId = codeHelper.createCode(codeName);

        // verify code created
        final GetCodesResponse newCode = codeHelper.retrieveCode(createResponseId);

        assertNotNull(newCode);
        assertEquals(createResponseId, newCode.getId(), "Verify value of codeId");
        assertEquals(codeName, newCode.getName(), "Verify code name");
        assertFalse(newCode.getSystemDefined(), "Verify system defined is false");

        // update code
        final PutCodesResponse updateChangeResponse = codeHelper.updateCode(createResponseId, codeName + "(CHANGE)");

        assertEquals(codeName + "(CHANGE)", updateChangeResponse.getChanges().getName(), "Verify code name updated");

        // delete code
        final Long deleteResponseId = codeHelper.deleteCode(createResponseId).getResourceId();
        assertEquals(createResponseId, deleteResponseId, "Verify code deleted");

        // verify code deleted
        final CallFailedRuntimeException deletedCodeError = codeHelper.retrieveCodeExpectingError(deleteResponseId);
        assertEquals(HTTP_NOT_FOUND, deletedCodeError.getStatus(), "Verify deleted code is gone");
    }

    // @Ignore()
    @Test
    // scenario 57, 60
    public void testPreventCreateDuplicateCode() {
        final String codeName = "Client Marital Status";

        // create code
        final Long createResponseId = codeHelper.createCode(codeName);

        // verify code created
        final GetCodesResponse newCode = codeHelper.retrieveCode(createResponseId);

        assertNotNull(newCode);
        assertEquals(createResponseId, newCode.getId(), "Verify value of codeId");
        assertEquals(codeName, newCode.getName(), "Verify code name");
        assertFalse(newCode.getSystemDefined(), "Verify system defined is false");

        // try to create duplicate-- should fail
        final CallFailedRuntimeException error = codeHelper.createCodeExpectingError(codeName);

        assertEquals("error.msg.code.duplicate.name", FeignErrors.errorGlobalisationCode(error), "Verify duplication error");

        // delete code that was just created
        final Long deleteResponseId = codeHelper.deleteCode(createResponseId).getResourceId();
        assertEquals(createResponseId, deleteResponseId, "Verify code deleted");

        // verify code deleted
        final CallFailedRuntimeException deletedCodeError = codeHelper.retrieveCodeExpectingError(deleteResponseId);
        assertEquals(HTTP_NOT_FOUND, deletedCodeError.getStatus(), "Verify deleted code is gone");
    }

    // @Ignore
    @Test
    public void testUpdateDeleteSystemDefinedCode() {

        // get any systemDefined code
        final GetCodesResponse systemDefinedCode = codeHelper.retrieveAnySystemDefinedCode();

        // delete system-defined code should fail
        final CallFailedRuntimeException error = codeHelper.deleteCodeExpectingError(systemDefinedCode.getId());

        assertEquals("error.msg.code.systemdefined", FeignErrors.errorGlobalisationCode(error), "Cannot delete system-defined code");

        // update system-defined code should fail
        final CallFailedRuntimeException updateError = codeHelper.updateCodeExpectingError(systemDefinedCode.getId(),
                systemDefinedCode.getName() + "CHANGE");

        assertEquals("error.msg.code.systemdefined", FeignErrors.errorGlobalisationCode(updateError), "Cannot update system-defined code");
    }

    // @Ignore
    @Test
    public void testCodeValuesNotAssignedToTable() {

        final String codeName = Utils.uniqueRandomStringGenerator("Marital Status1", 10);

        final String codeValue1 = "Married1";
        final String codeValue2 = "Unmarried1";

        final int codeValue1Position = 1;
        final int codeValue2Position = 1;

        final String codeDescription1 = "Description11";
        final String codeDescription2 = "Description22";

        // create code
        final Long createCodeResponseId = codeHelper.createCode(codeName);

        // create first code value
        final Long createCodeValueResponseId1 = codeHelper
                .createCodeValue(createCodeResponseId,
                        new PostCodeValuesDataRequest().name(codeValue1).description(codeDescription1).position(codeValue1Position))
                .getSubResourceId();

        // create second code value
        final Long createCodeValueResponseId2 = codeHelper
                .createCodeValue(createCodeResponseId,
                        new PostCodeValuesDataRequest().name(codeValue2).description(codeDescription2).position(codeValue1Position))
                .getSubResourceId();

        // verify two code values created
        final List<GetCodeValuesDataResponse> codeValuesList = codeHelper.retrieveAllCodeValues(createCodeResponseId);

        assertEquals(2, codeValuesList.size(), "Number of code values returned matches number created");

        // verify values of first code value
        final GetCodeValuesDataResponse codeValuesAttributes1 = codeHelper.retrieveCodeValue(createCodeResponseId,
                createCodeValueResponseId1);

        assertNotNull(codeValuesAttributes1);
        assertEquals(createCodeValueResponseId1, codeValuesAttributes1.getId(), "Verify value of codeValueId");
        assertEquals(codeValue1, codeValuesAttributes1.getName(), "Verify value of code name");
        assertEquals(codeDescription1, codeValuesAttributes1.getDescription(), "Verify value of code description");
        assertEquals(codeValue1Position, codeValuesAttributes1.getPosition(), "Verify position of code value");

        // verify values of second code value
        final GetCodeValuesDataResponse codeValuesAttributes2 = codeHelper.retrieveCodeValue(createCodeResponseId,
                createCodeValueResponseId2);

        assertNotNull(codeValuesAttributes2);
        assertEquals(createCodeValueResponseId2, codeValuesAttributes2.getId(), "Verify value of codeValueId");
        assertEquals(codeValue2, codeValuesAttributes2.getName(), "Verify value of code name");
        assertEquals(codeDescription2, codeValuesAttributes2.getDescription(), "Verify value of code description");
        assertEquals(codeValue2Position, codeValuesAttributes2.getPosition(), "Verify position of code value");

        // update code value 1
        final PutCodeValueDataResponse codeValueChanges = codeHelper.updateCodeValue(createCodeResponseId, createCodeValueResponseId1,
                new PutCodeValuesDataRequest().name(codeValue1 + "CHANGE").description(codeDescription1 + "CHANGE").position(4));

        assertEquals(codeValue1 + "CHANGE", codeValueChanges.getChanges().getName(), "Verify changed code value name");
        assertEquals(codeDescription1 + "CHANGE", codeValueChanges.getChanges().getDescription(), "Verify changed code value description");

        // delete code value; the server answers with the owning code as resourceId
        final DeleteCodeValueDataResponse deletedCodeValue1 = codeHelper.deleteCodeValue(createCodeResponseId, createCodeValueResponseId1);
        assertEquals(createCodeResponseId, deletedCodeValue1.getResourceId(), "Verify code of deleted code value");

        // Verify code value deleted
        final CallFailedRuntimeException deletedCodeValueError1 = codeHelper.retrieveCodeValueExpectingError(createCodeResponseId,
                createCodeValueResponseId1);

        assertEquals("error.msg.codevalue.id.invalid", FeignErrors.errorGlobalisationCode(deletedCodeValueError1));

        final List<GetCodeValuesDataResponse> deletedCodeValuesList = codeHelper.retrieveAllCodeValues(createCodeResponseId);

        assertEquals(1, deletedCodeValuesList.size(), "Number of code values is 1");

        final DeleteCodeValueDataResponse deletedCodeValue2 = codeHelper.deleteCodeValue(createCodeResponseId, createCodeValueResponseId2);
        assertEquals(createCodeResponseId, deletedCodeValue2.getResourceId(), "Verify code of deleted code value");

        final CallFailedRuntimeException deletedCodeValueError2 = codeHelper.retrieveCodeValueExpectingError(createCodeResponseId,
                createCodeValueResponseId2);

        assertEquals("error.msg.codevalue.id.invalid", FeignErrors.errorGlobalisationCode(deletedCodeValueError2));

        final List<GetCodeValuesDataResponse> deletedCodeValuesList1 = codeHelper.retrieveAllCodeValues(createCodeResponseId);

        assertEquals(0, deletedCodeValuesList1.size(), "Number of code values is 0");

    }

    @Disabled
    @Test
    public void testCodeValuesAssignedToTable() {

    }

}
