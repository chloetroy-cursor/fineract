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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.models.StaffCreateResponse;
import org.apache.fineract.client.models.StaffData;
import org.apache.fineract.client.models.StaffUpdateRequest;
import org.apache.fineract.client.models.StaffUpdateResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignRawHttpHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignStaffHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StaffTest {

    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_NOT_FOUND = 404;
    private static final Long HEAD_OFFICE_STAFF_ID = 1L;
    private static final Long MISSING_STAFF_ID = (long) Integer.MAX_VALUE;

    private FeignStaffHelper staffHelper;

    @BeforeEach
    public void setup() {
        staffHelper = new FeignStaffHelper(FineractFeignClientHelper.getFineractFeignClient());
    }

    @Test
    public void testStaffCreate() {
        StaffCreateResponse response = staffHelper.createStaff();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(FeignStaffHelper.DEFAULT_OFFICE_ID, response.getOfficeId());
        Assertions.assertNotNull(response.getResourceId());
    }

    @Test
    public void testStaffCreateValidationError() {
        assertBadRequest(staffHelper.createStaffExpectingError(FeignStaffHelper.defaultStaffCreateRequest().officeId(null)));
        assertBadRequest(staffHelper.createStaffExpectingError(FeignStaffHelper.defaultStaffCreateRequest().firstname(null)));
        assertBadRequest(staffHelper.createStaffExpectingError(FeignStaffHelper.defaultStaffCreateRequest().lastname(null)));

        /** Long firstname test */
        assertBadRequest(staffHelper.createStaffExpectingError(
                FeignStaffHelper.defaultStaffCreateRequest().firstname(Utils.uniqueRandomStringGenerator("michael_", 43))));

        /** Long lastname test */
        assertBadRequest(staffHelper.createStaffExpectingError(
                FeignStaffHelper.defaultStaffCreateRequest().lastname(Utils.uniqueRandomStringGenerator("Doe_", 47))));

        /** Long mobileNo test */
        assertBadRequest(staffHelper.createStaffExpectingError(
                FeignStaffHelper.defaultStaffCreateRequest().mobileNo(Utils.uniqueRandomStringGenerator("num_", 47))));
    }

    @Test
    public void testStaffCreateMaxNameLength() {
        StaffCreateRequest request = FeignStaffHelper.defaultStaffCreateRequest()//
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 42))//
                .lastname(Utils.uniqueRandomStringGenerator("Doe_", 46));

        Assertions.assertNotNull(staffHelper.createStaff(request).getResourceId());
    }

    @Test
    public void testStaffCreateExternalIdValidationError() {
        StaffCreateRequest request = FeignStaffHelper.defaultStaffCreateRequest().externalId(Utils.randomStringGenerator("EXT", 98));

        assertBadRequest(staffHelper.createStaffExpectingError(request));
    }

    @Test
    public void testStaffFetch() {
        StaffData response = staffHelper.getStaff(HEAD_OFFICE_STAFF_ID);
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HEAD_OFFICE_STAFF_ID, response.getId());
    }

    @Test
    public void testStaffListFetch() {
        Assertions.assertNotNull(staffHelper.getStaffList());
    }

    @Test
    public void testStaffListStatusAll() {
        Assertions.assertNotNull(staffHelper.getStaffListWithStatus("all"));
    }

    @Test
    public void testStaffListStatusActive() {
        List<StaffData> responseActive = staffHelper.getStaffListWithStatus("active");
        for (final StaffData staff : responseActive) {
            Assertions.assertNotNull(staff.getId());
            Assertions.assertEquals(true, staff.getIsActive());
        }
    }

    @Test
    public void testStaffListStatusInactive() {
        List<StaffData> responseInactive = staffHelper.getStaffListWithStatus("inactive");
        for (final StaffData staff : responseInactive) {
            Assertions.assertNotNull(staff.getId());
            Assertions.assertEquals(false, staff.getIsActive());
        }
    }

    @Test
    public void testStaffListFetchWrongState() {
        assertBadRequest(staffHelper.getStaffListWithStatusExpectingError("xyz"));
    }

    @Test
    public void testStaffFetchNotFound() {
        Assertions.assertEquals(HTTP_NOT_FOUND, staffHelper.getStaffExpectingError(MISSING_STAFF_ID).getStatus());
    }

    @Test
    public void testStaffUpdate() {
        final String firstname = Utils.uniqueRandomStringGenerator("michael_", 10);
        final String lastname = Utils.uniqueRandomStringGenerator("Doe_", 10);
        final String externalId = UUID.randomUUID().toString();
        final String mobileNo = "+14155552671";
        StaffUpdateRequest request = new StaffUpdateRequest().firstname(firstname).lastname(lastname).externalId(externalId)
                .mobileNo(mobileNo);

        StaffUpdateResponse response = staffHelper.updateStaff(HEAD_OFFICE_STAFF_ID, request);
        Map<String, Object> changes = response.getChanges();

        Assertions.assertEquals(HEAD_OFFICE_STAFF_ID, response.getResourceId());
        Assertions.assertEquals(firstname, changes.get("firstname"));
        Assertions.assertEquals(lastname, changes.get("lastname"));
        Assertions.assertEquals(externalId, changes.get("externalId"));
        Assertions.assertEquals(mobileNo, changes.get("mobileNo"));
    }

    @Test
    public void testStaffUpdateLongExternalIdError() {
        StaffUpdateRequest request = new StaffUpdateRequest().externalId(Utils.randomStringGenerator("EXT", 98));

        assertBadRequest(staffHelper.updateStaffExpectingError(HEAD_OFFICE_STAFF_ID, request));
    }

    @Test
    public void testStaffUpdateWrongActiveState() {
        // The typed request only accepts a Boolean, so the malformed value goes over raw HTTP.
        RuntimeException exception = Assertions.assertThrows(RuntimeException.class,
                () -> FeignRawHttpHelper.put("/staff/" + HEAD_OFFICE_STAFF_ID, "{\"isActive\":\"xyz\"}"));

        Assertions.assertTrue(exception.getMessage().startsWith("HTTP " + HTTP_BAD_REQUEST + " "), exception.getMessage());
    }

    @Test
    public void testStaffUpdateNotFoundError() {
        StaffUpdateRequest request = new StaffUpdateRequest().firstname(Utils.uniqueRandomStringGenerator("michael_", 5));

        Assertions.assertEquals(HTTP_NOT_FOUND, staffHelper.updateStaffExpectingError(MISSING_STAFF_ID, request).getStatus());
    }

    @Test
    public void testStaffUpdateValidationError() {
        final String firstname = Utils.uniqueRandomStringGenerator("michael_", 5);
        final String lastname = Utils.uniqueRandomStringGenerator("Doe_", 4);
        final String firstnameLong = Utils.uniqueRandomStringGenerator("michael_", 43);
        final String lastnameLong = Utils.uniqueRandomStringGenerator("Doe_", 47);

        /** Test long firstname */
        assertBadRequest(staffHelper.updateStaffExpectingError(HEAD_OFFICE_STAFF_ID,
                new StaffUpdateRequest().firstname(firstnameLong).lastname(lastname)));

        /** Test long lastname */
        assertBadRequest(staffHelper.updateStaffExpectingError(HEAD_OFFICE_STAFF_ID,
                new StaffUpdateRequest().firstname(firstname).lastname(lastnameLong)));

        /** Long mobileNo test */
        assertBadRequest(staffHelper.updateStaffExpectingError(HEAD_OFFICE_STAFF_ID,
                new StaffUpdateRequest().firstname(firstname).lastname(lastname).mobileNo(Utils.uniqueRandomStringGenerator("num_", 47))));
    }

    @Test
    public void testStaffLoanOfficer() {
        staffHelper.createStaff(FeignStaffHelper.defaultStaffCreateRequest().lastname(Utils.uniqueRandomStringGenerator("Doe_", 5)));

        List<StaffData> responseActive = staffHelper.getLoanOfficers();
        Assertions.assertFalse(responseActive.isEmpty());
        for (final StaffData staff : responseActive) {
            Assertions.assertNotNull(staff.getId());
            Assertions.assertEquals(true, staff.getIsLoanOfficer());
        }
    }

    private static void assertBadRequest(CallFailedRuntimeException exception) {
        Assertions.assertEquals(HTTP_BAD_REQUEST, exception.getStatus(), exception.getMessage());
    }
}
