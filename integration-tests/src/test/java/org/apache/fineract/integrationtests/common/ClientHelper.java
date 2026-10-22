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
package org.apache.fineract.integrationtests.common;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.client.feign.util.FeignCalls;
import org.apache.fineract.client.models.GetClientsClientIdAccountsResponse;
import org.apache.fineract.client.models.LoanAccountLockResponseDTO;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.util.Calls;

@RequiredArgsConstructor
public class ClientHelper {

    public static final String DEFAULT_OFFICE_ID = "1";
    public static final Long LEGALFORM_ID_PERSON = 1L;

    public static final String DEFAULT_DATE = "04 March 2011";

    private final RequestSpecification requestSpec;
    private final ResponseSpecification responseSpec;

    public static PostClientsResponse createClient(final PostClientsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().clients.createClient(request));
    }

    public static PostClientsResponse addClientAsPerson(final String officeId, final Long legalFormId, final String externalId) {
        PostClientsRequest request = new PostClientsRequest().officeId(officeId != null ? Long.parseLong(officeId) : 1L)
                .legalFormId(legalFormId).firstname(Utils.randomFirstNameGenerator()).lastname(Utils.randomLastNameGenerator())
                .externalId(externalId).dateFormat(Utils.DATE_FORMAT).locale("en").active(true).activationDate(DEFAULT_DATE);
        return createClient(request);
    }

    public static GetClientsClientIdAccountsResponse getClientAccounts(final String externalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().clients.retrieveAllClientAccountsByExternalId(externalId));
    }

    public static GetClientsClientIdAccountsResponse getClientAccounts(final long clientId) {
        return FeignCalls.ok(() -> FineractFeignClientHelper.getFineractFeignClient().clients().retrieveAllClientAccounts(clientId));
    }

    public static PostClientsRequest defaultClientCreationRequest() {
        return new PostClientsRequest().officeId(1L).legalFormId(LEGALFORM_ID_PERSON).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).externalId(UUID.randomUUID().toString()).dateFormat(Utils.DATE_FORMAT)
                .locale("en").active(true).activationDate(DEFAULT_DATE);
    }

    public LoanAccountLockResponseDTO retrieveLockedAccounts(int page, int limit) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanAccountLockApi.retrieveLockedAccounts(page, limit));
    }

}
