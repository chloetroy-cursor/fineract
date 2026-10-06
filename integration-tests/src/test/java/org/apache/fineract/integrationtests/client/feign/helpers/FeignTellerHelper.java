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

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdSummaryAndTransactionsResponse;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdTransactionsResponse;
import org.apache.fineract.client.models.PostTellersResponse;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersCashierIdAllocateRequest;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersCashierIdAllocateResponse;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersRequest;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersResponse;
import org.apache.fineract.integrationtests.client.feign.modules.FeignTestConstants;
import org.apache.fineract.integrationtests.common.Utils;

/** Typed Feign helper for tellers, cashiers and cashier cash allocation. */
public class FeignTellerHelper {

    public static final Long DEFAULT_OFFICE_ID = 1L;
    public static final String DEFAULT_CURRENCY_CODE = "USD";
    public static final LocalDate DEFAULT_TELLER_START_DATE = LocalDate.of(2011, 9, 20);
    public static final LocalDate DEFAULT_CASHIER_START_DATE = LocalDate.of(2023, 1, 1);
    public static final LocalDate DEFAULT_CASHIER_END_DATE = LocalDate.of(2023, 12, 31);
    public static final LocalDate DEFAULT_ALLOCATION_DATE = LocalDate.of(2023, 1, 1);

    private static final int TELLER_STATUS_ACTIVE = 300;

    private final FineractFeignClient fineractClient;
    private final TellerCommandsApi tellerCommands;

    public FeignTellerHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
        this.tellerCommands = fineractClient.create(TellerCommandsApi.class);
    }

    public PostTellersResponse createTeller() {
        return createTeller(new TellerCommandsApi.CreateTellerRequest()//
                .officeId(DEFAULT_OFFICE_ID)//
                .name(Utils.uniqueRandomStringGenerator("Teller 1", 5))//
                .description(Utils.uniqueRandomStringGenerator("Teller For Testing", 4))//
                .status(TELLER_STATUS_ACTIVE)//
                .startDate(DEFAULT_TELLER_START_DATE.toString())//
                .dateFormat(FeignTestConstants.ISO_DATE_PATTERN)//
                .locale(FeignTestConstants.LOCALE));
    }

    public PostTellersResponse createTeller(TellerCommandsApi.CreateTellerRequest request) {
        return ok(() -> tellerCommands.createTeller(request));
    }

    public PostTellersTellerIdCashiersResponse createCashier(Long tellerId, Long staffId) {
        return createCashier(tellerId, new PostTellersTellerIdCashiersRequest()//
                .staffId(staffId)//
                .description(Utils.uniqueRandomStringGenerator("test__", 4))//
                .startDate(DEFAULT_CASHIER_START_DATE)//
                .endDate(DEFAULT_CASHIER_END_DATE)//
                .isFullDay(true)//
                .dateFormat(FeignTestConstants.ISO_DATE_PATTERN)//
                .locale(FeignTestConstants.LOCALE));
    }

    public PostTellersTellerIdCashiersResponse createCashier(Long tellerId, PostTellersTellerIdCashiersRequest request) {
        return ok(() -> fineractClient.tellerCashManagement().createCashierForTeller(tellerId, request));
    }

    public static PostTellersTellerIdCashiersCashierIdAllocateRequest allocateCashRequest(BigDecimal txnAmount) {
        return new PostTellersTellerIdCashiersCashierIdAllocateRequest()//
                .currencyCode(DEFAULT_CURRENCY_CODE)//
                .txnAmount(txnAmount)//
                .txnDate(DEFAULT_ALLOCATION_DATE)//
                .txnNote(Utils.uniqueRandomStringGenerator("Allocate cash ", 4))//
                .dateFormat(FeignTestConstants.ISO_DATE_PATTERN)//
                .locale(FeignTestConstants.LOCALE);
    }

    public PostTellersTellerIdCashiersCashierIdAllocateResponse allocateCashToCashier(Long tellerId, Long cashierId,
            PostTellersTellerIdCashiersCashierIdAllocateRequest request) {
        return ok(() -> fineractClient.tellerCashManagement().allocateCashToCashier(tellerId, cashierId, request));
    }

    /** Sends {@code rawJsonBody} verbatim and returns the failure the server answered with. */
    public CallFailedRuntimeException allocateCashToCashierExpectingError(Long tellerId, Long cashierId, String rawJsonBody) {
        return fail(() -> tellerCommands.allocateCashToCashier(tellerId, cashierId, rawJsonBody));
    }

    public GetTellersTellerIdCashiersCashiersIdTransactionsResponse retrieveCashierTransactions(Long tellerId, Long cashierId,
            String currencyCode, Integer offset, Integer limit, String orderBy, String sortOrder) {
        return ok(() -> fineractClient.tellerCashManagement().retrieveCashierTransactions(tellerId, cashierId, currencyCode, offset, limit,
                orderBy, sortOrder));
    }

    public GetTellersTellerIdCashiersCashiersIdSummaryAndTransactionsResponse retrieveCashierTransactionsWithSummary(Long tellerId,
            Long cashierId, String currencyCode, Integer offset, Integer limit, String orderBy, String sortOrder) {
        return ok(() -> fineractClient.tellerCashManagement().retrieveCashierTransactionsWithSummary(tellerId, cashierId, currencyCode,
                offset, limit, orderBy, sortOrder));
    }
}
