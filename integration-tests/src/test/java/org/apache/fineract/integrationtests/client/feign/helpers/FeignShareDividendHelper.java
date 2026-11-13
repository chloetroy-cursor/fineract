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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.feign.services.SelfDividendApi;

/**
 * Share product dividends: {@code /v1/shareproduct/{productId}/dividend}.
 *
 * <p>
 * {@code ShareDividendApiResource} declares every request and response body as {@code String}, so the generated
 * {@link SelfDividendApi} carries raw JSON in both directions. This helper builds the request bodies from typed
 * arguments and hands the responses back as a Jackson tree, the same way {@link FeignDatatableHelper} does for its
 * untyped endpoint.
 */
public class FeignShareDividendHelper {

    private static final String APPROVE = "approve";

    private final SelfDividendApi dividendApi;

    public FeignShareDividendHelper(FineractFeignClient fineractClient) {
        this.dividendApi = fineractClient.create(SelfDividendApi.class);
    }

    /** Creates a dividend payout for the product and returns the dividend id ({@code subResourceId}). */
    public Long createDividend(Long productId, String dividendPeriodStartDate, String dividendPeriodEndDate, BigDecimal dividendAmount,
            String dateFormat, String locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dividendPeriodStartDate", dividendPeriodStartDate);
        body.put("dividendPeriodEndDate", dividendPeriodEndDate);
        body.put("dividendAmount", dividendAmount);
        body.put("dateFormat", dateFormat);
        body.put("locale", locale);
        String response = ok(() -> dividendApi.createShareDividend(productId, asJson(body)));
        return parse(response, "dividend creation").get("subResourceId").asLong();
    }

    public void approveDividend(Long productId, Long dividendId) {
        ok(() -> dividendApi.updateShareDividend(productId, dividendId, APPROVE, "{}"));
    }

    /** Page of the product's dividend payouts: {@code totalFilteredRecords} and {@code pageItems}. */
    public JsonNode getDividends(Long productId) {
        String response = ok(() -> dividendApi.retrieveAllShareDividends(productId, null, null, null, null, null));
        return parse(response, "dividends of share product " + productId);
    }

    /**
     * Page of the per-share-account amounts of one dividend payout: {@code totalFilteredRecords} and {@code pageItems}.
     */
    public JsonNode getDividendDetails(Long productId, Long dividendId) {
        String response = ok(() -> dividendApi.retrieveOneShareDividend(dividendId, productId, null, null, null, null, null));
        return parse(response, "details of share dividend " + dividendId);
    }

    private static String asJson(Map<String, Object> body) {
        try {
            return ObjectMapperFactory.getShared().writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise the share dividend request " + body, e);
        }
    }

    private static JsonNode parse(String json, String what) {
        try {
            return ObjectMapperFactory.getShared().readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse the response for " + what + ": " + json, e);
        }
    }
}
