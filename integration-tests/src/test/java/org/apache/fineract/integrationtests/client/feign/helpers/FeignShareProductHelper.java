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

import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.GetProductsTypeProductIdResponse;
import org.apache.fineract.client.models.PostProductsTypeRequest;

public class FeignShareProductHelper {

    /** Share products are reached through the generic products resource, keyed by this type. */
    private static final String SHARE = "share";

    private final FineractFeignClient fineractClient;

    public FeignShareProductHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public Long createShareProduct(PostProductsTypeRequest request) {
        return ok(() -> fineractClient.products().createShareProduct(SHARE, request)).getResourceId();
    }

    public GetProductsTypeProductIdResponse getShareProduct(Long shareProductId) {
        return ok(() -> fineractClient.products().retrieveOneShareProduct(shareProductId, SHARE));
    }

    /** See {@link ShareProductCommandsApi#updateShareCounts} for why this update needs its own request model. */
    public Long updateShareCounts(Long shareProductId, long totalShares, long sharesIssued, String locale) {
        ShareProductCommandsApi.UpdateShareCountsRequest request = new ShareProductCommandsApi.UpdateShareCountsRequest()//
                .locale(locale)//
                .totalShares(totalShares)//
                .sharesIssued(sharesIssued);
        return ok(() -> fineractClient.create(ShareProductCommandsApi.class).updateShareCounts(SHARE, shareProductId, request))
                .getResourceId();
    }
}
