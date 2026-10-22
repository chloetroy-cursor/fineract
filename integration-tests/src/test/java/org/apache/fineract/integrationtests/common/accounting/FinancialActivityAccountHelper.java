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
package org.apache.fineract.integrationtests.common.accounting;

import io.restassured.specification.RequestSpecification;
import java.util.List;
import org.apache.fineract.client.models.DeleteFinancialActivityAccountsResponse;
import org.apache.fineract.client.models.GetFinancialActivityAccountsResponse;
import org.apache.fineract.client.models.PostFinancialActivityAccountsRequest;
import org.apache.fineract.client.models.PostFinancialActivityAccountsResponse;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.FineractClientHelper;

public class FinancialActivityAccountHelper {

    // Existing tests still construct this helper with a REST Assured spec. The methods below use the
    // generated client and ignore that spec, so the parameter stays to avoid touching those call sites.
    public FinancialActivityAccountHelper(@SuppressWarnings("unused") final RequestSpecification requestSpec) {}

    public PostFinancialActivityAccountsResponse createFinancialActivityAccount(PostFinancialActivityAccountsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().financialActivyAccountMappings
                .createGLAccountMappingFinancialActivityAccount(request));
    }

    public List<GetFinancialActivityAccountsResponse> getAllFinancialActivityAccounts() {
        return Calls.ok(FineractClientHelper.getFineractClient().financialActivyAccountMappings.retrieveAll());
    }

    public DeleteFinancialActivityAccountsResponse deleteFinancialActivityAccount(Long financialMappingId) {
        return Calls.ok(FineractClientHelper.getFineractClient().financialActivyAccountMappings
                .deleteGLAccountMappingFinancialActivityAccount(financialMappingId));
    }
}
