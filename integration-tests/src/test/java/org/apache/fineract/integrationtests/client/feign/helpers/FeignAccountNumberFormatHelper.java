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
import org.apache.fineract.client.models.DeleteAccountNumberFormatsResponse;
import org.apache.fineract.client.models.GetAccountNumberFormatsIdResponse;
import org.apache.fineract.client.models.PostAccountNumberFormatsRequest;
import org.apache.fineract.client.models.PutAccountNumberFormatsRequest;
import org.apache.fineract.client.models.PutAccountNumberFormatsResponse;

/** Drives {@code /accountnumberformats}, the account number preferences of the server. */
public class FeignAccountNumberFormatHelper {

    /** {@code EntityAccountType} ids on the server. */
    public static final long ACCOUNT_TYPE_CLIENT = 1L;
    public static final long ACCOUNT_TYPE_LOAN = 2L;
    public static final long ACCOUNT_TYPE_SAVINGS = 3L;
    public static final long ACCOUNT_TYPE_CENTER = 4L;
    public static final long ACCOUNT_TYPE_GROUP = 5L;

    /** {@code AccountNumberPrefixType} ids on the server. */
    public static final long PREFIX_TYPE_OFFICE_NAME = 1L;
    public static final long PREFIX_TYPE_CLIENT_TYPE = 101L;
    public static final long PREFIX_TYPE_LOAN_PRODUCT_SHORT_NAME = 201L;
    public static final long PREFIX_TYPE_SAVINGS_PRODUCT_SHORT_NAME = 301L;

    private final FineractFeignClient fineractClient;

    public FeignAccountNumberFormatHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public Long createAccountNumberFormat(long accountType, long prefixType) {
        return ok(() -> fineractClient.accountNumberFormat().createAccountNumberFormat(request(accountType, prefixType))).getResourceId();
    }

    public CallFailedRuntimeException createAccountNumberFormatExpectingError(long accountType, long prefixType) {
        return fail(() -> fineractClient.accountNumberFormat().createAccountNumberFormat(request(accountType, prefixType)));
    }

    public GetAccountNumberFormatsIdResponse retrieveAccountNumberFormat(Long accountNumberFormatId) {
        return ok(() -> fineractClient.accountNumberFormat().retrieveOneAccountNumberFormat(accountNumberFormatId));
    }

    public List<GetAccountNumberFormatsIdResponse> retrieveAllAccountNumberFormats() {
        return ok(() -> fineractClient.accountNumberFormat().retrieveAllAccountNumberFormats());
    }

    public PutAccountNumberFormatsResponse updateAccountNumberFormat(Long accountNumberFormatId, long prefixType) {
        return ok(() -> fineractClient.accountNumberFormat().updateAccountNumberFormat(accountNumberFormatId,
                new PutAccountNumberFormatsRequest().prefixType(prefixType)));
    }

    public CallFailedRuntimeException updateAccountNumberFormatExpectingError(Long accountNumberFormatId, long prefixType) {
        return fail(() -> fineractClient.accountNumberFormat().updateAccountNumberFormat(accountNumberFormatId,
                new PutAccountNumberFormatsRequest().prefixType(prefixType)));
    }

    public DeleteAccountNumberFormatsResponse deleteAccountNumberFormat(Long accountNumberFormatId) {
        return ok(() -> fineractClient.accountNumberFormat().deleteAccountNumberFormat(accountNumberFormatId));
    }

    public CallFailedRuntimeException deleteAccountNumberFormatExpectingError(Long accountNumberFormatId) {
        return fail(() -> fineractClient.accountNumberFormat().deleteAccountNumberFormat(accountNumberFormatId));
    }

    private static PostAccountNumberFormatsRequest request(long accountType, long prefixType) {
        return new PostAccountNumberFormatsRequest().accountType(accountType).prefixType(prefixType);
    }
}
