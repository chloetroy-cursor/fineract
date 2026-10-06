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

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.AccountRequest;
import org.apache.fineract.client.models.GetAccountsTypeAccountIdResponse;
import org.apache.fineract.client.models.PostAccountsRequestedShares;
import org.apache.fineract.client.models.PostAccountsTypeAccountIdRequest;
import org.apache.fineract.client.models.PostProductsTypeRequest;
import org.apache.fineract.client.models.PutAccountsTypeAccountIdRequest;
import org.apache.fineract.client.models.PutAccountsTypeAccountIdResponse;

public class FeignShareAccountHelper {

    /** Share products and accounts are reached through the generic products/accounts resources, keyed by this type. */
    private static final String SHARE = "share";

    private final FineractFeignClient fineractClient;

    public FeignShareAccountHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public Long createShareProduct(PostProductsTypeRequest request) {
        return ok(() -> fineractClient.products().createShareProduct(SHARE, request)).getResourceId();
    }

    public Long applyShareAccount(AccountRequest request) {
        return ok(() -> fineractClient.shareAccount().createShareAccount(SHARE, request)).getResourceId();
    }

    public PutAccountsTypeAccountIdResponse updateShareAccount(Long shareAccountId, PutAccountsTypeAccountIdRequest request) {
        return ok(() -> fineractClient.shareAccount().updateShareAccount(SHARE, shareAccountId, request));
    }

    public void approve(Long shareAccountId) {
        command(shareAccountId, new PostAccountsTypeAccountIdRequest(), "approve");
    }

    public void approve(Long shareAccountId, String approvedDate, String note, String dateFormat, String locale) {
        command(shareAccountId,
                new PostAccountsTypeAccountIdRequest().approvedDate(approvedDate).note(note).dateFormat(dateFormat).locale(locale),
                "approve");
    }

    public void undoApproval(Long shareAccountId) {
        command(shareAccountId, new PostAccountsTypeAccountIdRequest(), "undoapproval");
    }

    public void reject(Long shareAccountId, String note) {
        command(shareAccountId, new PostAccountsTypeAccountIdRequest().note(note), "reject");
    }

    public void activate(Long shareAccountId, String activatedDate, String dateFormat, String locale) {
        command(shareAccountId, new PostAccountsTypeAccountIdRequest().activatedDate(activatedDate).dateFormat(dateFormat).locale(locale),
                "activate");
    }

    public void close(Long shareAccountId, String closedDate, String note, String dateFormat, String locale) {
        command(shareAccountId,
                new PostAccountsTypeAccountIdRequest().closedDate(closedDate).note(note).dateFormat(dateFormat).locale(locale), "close");
    }

    /** {@code purchaseTransactionIds} are the ids of the pending purchase transactions, not a share count. */
    public void approveAdditionalShares(Long shareAccountId, Collection<Long> purchaseTransactionIds) {
        command(shareAccountId, new PostAccountsTypeAccountIdRequest().requestedShares(transactionIds(purchaseTransactionIds)),
                "approveadditionalshares");
    }

    /** {@code purchaseTransactionIds} are the ids of the pending purchase transactions, not a share count. */
    public void rejectAdditionalShares(Long shareAccountId, Collection<Long> purchaseTransactionIds) {
        command(shareAccountId, new PostAccountsTypeAccountIdRequest().requestedShares(transactionIds(purchaseTransactionIds)),
                "rejectadditionalshares");
    }

    private static Set<PostAccountsRequestedShares> transactionIds(Collection<Long> purchaseTransactionIds) {
        return purchaseTransactionIds.stream().map(id -> new PostAccountsRequestedShares().id(id)).collect(Collectors.toSet());
    }

    private void command(Long shareAccountId, PostAccountsTypeAccountIdRequest request, String command) {
        ok(() -> fineractClient.shareAccount().handleCommandsShareAccount(SHARE, shareAccountId, request, command));
    }

    /** See {@link ShareAccountCommandsApi#applyAdditionalShares} for why this command needs its own request model. */
    public void applyAdditionalShares(Long shareAccountId, long shares, String requestedDate, String dateFormat, String locale) {
        ShareAccountCommandsApi.ShareCountRequest request = shareCount(shares, requestedDate, dateFormat, locale);
        ok(() -> fineractClient.create(ShareAccountCommandsApi.class).applyAdditionalShares(SHARE, shareAccountId, request));
    }

    /** See {@link ShareAccountCommandsApi#redeemShares} for why this command needs its own request model. */
    public void redeemShares(Long shareAccountId, long shares, String requestedDate, String dateFormat, String locale) {
        ShareAccountCommandsApi.ShareCountRequest request = shareCount(shares, requestedDate, dateFormat, locale);
        ok(() -> fineractClient.create(ShareAccountCommandsApi.class).redeemShares(SHARE, shareAccountId, request));
    }

    private static ShareAccountCommandsApi.ShareCountRequest shareCount(long shares, String requestedDate, String dateFormat,
            String locale) {
        return new ShareAccountCommandsApi.ShareCountRequest()//
                .requestedDate(requestedDate)//
                .dateFormat(dateFormat)//
                .locale(locale)//
                .requestedShares(shares);
    }

    public GetAccountsTypeAccountIdResponse getShareAccount(Long shareAccountId) {
        return ok(() -> fineractClient.shareAccount().retrieveOneShareAccount(shareAccountId, SHARE));
    }
}
