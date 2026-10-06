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

import java.util.Calendar;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.client.models.DeleteGLAccountsResponse;
import org.apache.fineract.client.models.GetGLAccountsResponse;
import org.apache.fineract.client.models.PostGLAccountsRequest;
import org.apache.fineract.client.models.PostGLAccountsResponse;
import org.apache.fineract.client.models.PutGLAccountsRequest;
import org.apache.fineract.client.models.PutGLAccountsResponse;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Assertions;

public final class AccountHelper {

    private AccountHelper() {}

    public static PostGLAccountsResponse createGLAccount(final PostGLAccountsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().glAccounts.createGLAccount(request));
    }

    public static DeleteGLAccountsResponse deleteGLAccount(final Long requestId) {
        return Calls.ok(FineractClientHelper.getFineractClient().glAccounts.deleteGLAccount(requestId));
    }

    public static PutGLAccountsResponse updateGLAccount(final Long requestId, final PutGLAccountsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().glAccounts.updateGLAccount(requestId, request));
    }

    public static GetGLAccountsResponse getGLAccount(final Long glAccountId) {
        return Calls.ok(FineractClientHelper.getFineractClient().glAccounts.retreiveAccount(glAccountId, false));
    }

    public static Account createAssetGlAccount(final String glAccountName) {
        PostGLAccountsResponse postGLAccountsResponse = createGLAccount(
                createGlAccount(GLAccountType.ASSET, Utils.uniqueRandomStringGenerator(glAccountName, 6), null));
        Assertions.assertNotNull(postGLAccountsResponse);
        return new Account(postGLAccountsResponse.getResourceId().intValue(), Account.AccountType.ASSET);
    }

    public static Account createLiabilityGlAccount(final String glAccountName) {
        PostGLAccountsResponse postGLAccountsResponse = createGLAccount(
                createGlAccount(GLAccountType.LIABILITY, Utils.uniqueRandomStringGenerator(glAccountName, 6), null));
        Assertions.assertNotNull(postGLAccountsResponse);
        return new Account(postGLAccountsResponse.getResourceId().intValue(), Account.AccountType.LIABILITY);
    }

    public static Account createIncomeGlAccount(final String glAccountName) {
        PostGLAccountsResponse postGLAccountsResponse = createGLAccount(
                createGlAccount(GLAccountType.INCOME, Utils.uniqueRandomStringGenerator(glAccountName, 6), null));
        Assertions.assertNotNull(postGLAccountsResponse);
        return new Account(postGLAccountsResponse.getResourceId().intValue(), Account.AccountType.INCOME);
    }

    public static Account createExpenseGlAccount(final String glAccountName) {
        PostGLAccountsResponse postGLAccountsResponse = createGLAccount(
                createGlAccount(GLAccountType.EXPENSE, Utils.uniqueRandomStringGenerator(glAccountName, 6), null));
        Assertions.assertNotNull(postGLAccountsResponse);
        return new Account(postGLAccountsResponse.getResourceId().intValue(), Account.AccountType.EXPENSE);
    }

    public static Account createEquityGlAccount(final String glAccountName) {
        PostGLAccountsResponse postGLAccountsResponse = createGLAccount(
                createGlAccount(GLAccountType.EQUITY, Utils.uniqueRandomStringGenerator(glAccountName, 6), null));
        Assertions.assertNotNull(postGLAccountsResponse);
        return new Account(postGLAccountsResponse.getResourceId().intValue(), Account.AccountType.EQUITY);
    }

    public static PostGLAccountsRequest createGlAccount(final GLAccountType glAccountType, final String glAccountName,
            final Long parentAccountId) {
        return new PostGLAccountsRequest().type(glAccountType.getValue()).glCode(createGlCode(glAccountType)).manualEntriesAllowed(true)
                .usage(1).parentId(parentAccountId).description(glAccountName).name(glAccountName);
    }

    public static String createGlCode(final GLAccountType glAccountType) {
        return Utils.uniqueRandomStringGenerator(glAccountType.getValue().toString() + Calendar.getInstance().getTimeInMillis(), 2);
    }
}
