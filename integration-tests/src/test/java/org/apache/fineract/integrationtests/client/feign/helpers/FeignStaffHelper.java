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
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.models.StaffCreateResponse;
import org.apache.fineract.client.models.StaffData;
import org.apache.fineract.client.models.StaffUpdateRequest;
import org.apache.fineract.client.models.StaffUpdateResponse;
import org.apache.fineract.integrationtests.client.feign.modules.LoanTestData;
import org.apache.fineract.integrationtests.common.Utils;

/** Typed Feign helper for staff operations. */
public class FeignStaffHelper {

    public static final Long DEFAULT_OFFICE_ID = 1L;
    public static final String DEFAULT_JOINING_DATE = "20 September 2011";

    private final FineractFeignClient fineractClient;

    public FeignStaffHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    /** A valid loan-officer request in the head office; callers adjust fields before sending it. */
    public static StaffCreateRequest defaultStaffCreateRequest() {
        return new StaffCreateRequest()//
                .officeId(DEFAULT_OFFICE_ID)//
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5))//
                .lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))//
                .isLoanOfficer(true)//
                .joiningDate(DEFAULT_JOINING_DATE)//
                .dateFormat(LoanTestData.DATETIME_PATTERN)//
                .locale(LoanTestData.LOCALE);
    }

    public StaffCreateResponse createStaff() {
        return createStaff(DEFAULT_OFFICE_ID, DEFAULT_JOINING_DATE);
    }

    public StaffCreateResponse createStaff(Long officeId, String joiningDate) {
        return createStaff(defaultStaffCreateRequest().officeId(officeId).joiningDate(joiningDate));
    }

    public StaffCreateResponse createStaff(StaffCreateRequest request) {
        return ok(() -> fineractClient.staff().createStaff(request));
    }

    public CallFailedRuntimeException createStaffExpectingError(StaffCreateRequest request) {
        return fail(() -> fineractClient.staff().createStaff(request));
    }

    public StaffData getStaff(Long staffId) {
        return ok(() -> fineractClient.staff().retrieveOneStaff(staffId));
    }

    public CallFailedRuntimeException getStaffExpectingError(Long staffId) {
        return fail(() -> fineractClient.staff().retrieveOneStaff(staffId));
    }

    public List<StaffData> getStaffList() {
        return ok(() -> fineractClient.staff().retrieveAllStaff(null, false, false, null));
    }

    /** {@code status} is {@code active}, {@code inactive} or {@code all}. */
    public List<StaffData> getStaffListWithStatus(String status) {
        return ok(() -> fineractClient.staff().retrieveAllStaff(null, false, false, status));
    }

    public CallFailedRuntimeException getStaffListWithStatusExpectingError(String status) {
        return fail(() -> fineractClient.staff().retrieveAllStaff(null, false, false, status));
    }

    public List<StaffData> getLoanOfficers() {
        return ok(() -> fineractClient.staff().retrieveAllStaff(null, false, true, null));
    }

    public StaffUpdateResponse updateStaff(Long staffId, StaffUpdateRequest request) {
        return ok(() -> fineractClient.staff().updateStaff(staffId, request));
    }

    public CallFailedRuntimeException updateStaffExpectingError(Long staffId, StaffUpdateRequest request) {
        return fail(() -> fineractClient.staff().updateStaff(staffId, request));
    }
}
