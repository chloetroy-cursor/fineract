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
package org.apache.fineract.integrationtests.organization.teller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdTransactionsResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignTellerHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CashierSummaryAndTransactionsTest {

    private FeignTellerHelper tellerHelper;
    private Long tellerId;
    private Long cashierId;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();

        final RequestSpecification requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpecification.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        final ResponseSpecification responseSpecification = new ResponseSpecBuilder().expectStatusCode(200).build();

        tellerHelper = new FeignTellerHelper(FineractFeignClientHelper.getFineractFeignClient());
        final Long staffId = Long.valueOf(StaffHelper.createStaff(requestSpecification, responseSpecification));
        tellerId = tellerHelper.createTeller().getResourceId();
        cashierId = tellerHelper.createCashier(tellerId, staffId).getSubResourceId();
    }

    @Test
    public void testGetCashierTransactions() {
        final GetTellersTellerIdCashiersCashiersIdTransactionsResponse result = tellerHelper.retrieveCashierTransactions(tellerId,
                cashierId, "UGX", 0, 0, null, null);
        assertNotNull(result);
    }

}
