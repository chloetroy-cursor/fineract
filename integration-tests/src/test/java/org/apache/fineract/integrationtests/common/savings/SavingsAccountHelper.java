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
package org.apache.fineract.integrationtests.common.savings;

import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.PagedLocalRequestAdvancedQueryRequest;
import org.apache.fineract.client.models.PostSavingsAccountTransactionsRequest;
import org.apache.fineract.client.models.PostSavingsAccountTransactionsResponse;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import retrofit2.Response;

/**
 * Savings account calls that are not yet covered by the Feign helpers under
 * {@code org.apache.fineract.integrationtests.client.feign.helpers}. Prefer {@code FeignSavingsHelper} and
 * {@code FeignSavingsTransactionHelper} for new code.
 */
public class SavingsAccountHelper {

    public static List<Long> getSavingsIdsByStatusId(int status) {
        return Calls.ok(FineractClientHelper.getFineractClient().legacy.getSavingsAccountsByStatus(status));
    }

    public PostSavingsAccountsAccountIdResponse closeSavingsAccount(final Long savingsId, PostSavingsAccountsAccountIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().savingsAccounts.handleCommandsSavingsAccount(savingsId, request, "close"));
    }

    public Response<PostSavingsAccountTransactionsResponse> withdrawalFromSavingsAccount(final Long savingsId,
            PostSavingsAccountTransactionsRequest request) {
        return Calls.executeU(FineractClientHelper.getFineractClient().savingsTransactions.createSavingsAccountTransaction(savingsId,
                request, "withdrawal"));
    }

    public Response<PostSavingsAccountTransactionsResponse> forceWithdrawalFromSavingsAccount(final Long savingsId,
            PostSavingsAccountTransactionsRequest request) {
        return Calls.executeU(FineractClientHelper.getFineractClient().savingsTransactions.createSavingsAccountTransaction(savingsId,
                request, "force-withdrawal"));
    }

    public Response<PostSavingsAccountTransactionsResponse> depositIntoSavingsAccount(final Long savingsId,
            PostSavingsAccountTransactionsRequest request) {
        return Calls.executeU(FineractClientHelper.getFineractClient().savingsTransactions.createSavingsAccountTransaction(savingsId,
                request, "deposit"));
    }

    public Map<String, Object> querySavingsTransactions(Integer savingsId, PagedLocalRequestAdvancedQueryRequest request) {
        String response = Calls.ok(FineractClientHelper.getFineractClient().savingsTransactions
                .advancedQuerySavingsAccountTransactions(savingsId.longValue(), request));
        return JsonPath.from(response).get("");
    }
}
