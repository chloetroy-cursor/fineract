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

import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.InteropIdentifierAccountResponseData;
import org.apache.fineract.client.models.InteropIdentifierRequestData;
import org.apache.fineract.client.models.InteropQuoteRequestData;
import org.apache.fineract.client.models.InteropQuoteResponseData;
import org.apache.fineract.client.models.InteropTransactionRequestData;
import org.apache.fineract.client.models.InteropTransactionRequestResponseData;
import org.apache.fineract.client.models.InteropTransferRequestData;
import org.apache.fineract.client.models.InteropTransferResponseData;
import org.apache.fineract.integrationtests.client.feign.helpers.InteropCommandsApi.FormattedExpirationTransactionRequest;
import org.apache.fineract.interoperation.domain.InteropTransferActionType;

/**
 * The Mojaloop-style interoperation API: party (secondary identifier) registration, transaction requests, quotes and
 * two-phase transfers against a savings account addressed by its external id.
 */
public class FeignInteropHelper {

    private final FineractFeignClient fineractClient;
    private final InteropCommandsApi commandsApi;

    public FeignInteropHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
        this.commandsApi = fineractClient.create(InteropCommandsApi.class);
    }

    public InteropIdentifierAccountResponseData registerAccountIdentifier(InteropIdentifierRequestData.IdTypeEnum idType, String idValue,
            String accountExternalId) {
        return ok(() -> fineractClient.interOperation().registerAccountIdentifier(idType.getValue(), idValue,
                identifierRequest(accountExternalId)));
    }

    public CallFailedRuntimeException registerAccountIdentifierExpectingError(InteropIdentifierRequestData.IdTypeEnum idType,
            String idValue, String accountExternalId) {
        return fail(() -> fineractClient.interOperation().registerAccountIdentifier(idType.getValue(), idValue,
                identifierRequest(accountExternalId)));
    }

    public InteropIdentifierAccountResponseData getAccountByIdentifier(InteropIdentifierRequestData.IdTypeEnum idType, String idValue) {
        return ok(() -> fineractClient.interOperation().getAccountByIdentifier(idType.getValue(), idValue));
    }

    public CallFailedRuntimeException getAccountByIdentifierExpectingError(InteropIdentifierRequestData.IdTypeEnum idType, String idValue) {
        return fail(() -> fineractClient.interOperation().getAccountByIdentifier(idType.getValue(), idValue));
    }

    public InteropIdentifierAccountResponseData deleteAccountIdentifier(InteropIdentifierRequestData.IdTypeEnum idType, String idValue,
            String accountExternalId) {
        return ok(() -> fineractClient.interOperation().deleteAccountIdentifier(idType.getValue(), idValue,
                identifierRequest(accountExternalId)));
    }

    public InteropTransactionRequestResponseData createTransactionRequest(InteropTransactionRequestData request) {
        return ok(() -> fineractClient.interOperation().createTransactionRequest(request));
    }

    public CallFailedRuntimeException createTransactionRequestExpectingError(InteropTransactionRequestData request) {
        return fail(() -> fineractClient.interOperation().createTransactionRequest(request));
    }

    public CallFailedRuntimeException createTransactionRequestExpectingError(FormattedExpirationTransactionRequest request) {
        return fail(() -> commandsApi.createTransactionRequestWithFormattedExpiration(request));
    }

    public InteropQuoteResponseData createQuote(InteropQuoteRequestData request) {
        return ok(() -> fineractClient.interOperation().createQuote(request));
    }

    public InteropTransferResponseData prepareTransfer(InteropTransferRequestData request) {
        return performTransfer(InteropTransferActionType.PREPARE, request);
    }

    public InteropTransferResponseData createTransfer(InteropTransferRequestData request) {
        return performTransfer(InteropTransferActionType.CREATE, request);
    }

    public InteropTransferResponseData performTransfer(InteropTransferActionType action, InteropTransferRequestData request) {
        return ok(() -> fineractClient.interOperation().performTransfer(request, action.name()));
    }

    /**
     * @param action
     *            the raw {@code action} query parameter; {@code null} leaves it out of the request entirely
     */
    public CallFailedRuntimeException performTransferExpectingError(String action, InteropTransferRequestData request) {
        return fail(() -> fineractClient.interOperation().performTransfer(request, action));
    }

    private static InteropIdentifierRequestData identifierRequest(String accountExternalId) {
        return new InteropIdentifierRequestData().accountId(accountExternalId);
    }
}
