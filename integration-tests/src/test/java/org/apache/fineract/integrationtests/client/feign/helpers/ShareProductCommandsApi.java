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

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import org.apache.fineract.client.models.PutProductsTypeProductIdResponse;

/**
 * Feign interface for the share-product update whose body the generated client cannot express. It binds to the
 * generated response model, so the call stays typed end to end and does not build a JSON body by hand.
 *
 * <p>
 * Create and read go through the generated {@code fineractClient.products()} API.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface ShareProductCommandsApi {

    /**
     * Updates the share counts of a share product.
     *
     * <p>
     * {@code ShareProductDataSerializer.validateAndUpdate} accepts every field the create command does, but the
     * documented {@code PutProductsTypeProductIdRequest} only lists {@code description}, {@code locale} and
     * {@code unitPrice}, so the generated model has no way to carry {@code totalShares} or {@code sharesIssued}.
     */
    @RequestLine("PUT /v1/products/{type}/{productId}")
    PutProductsTypeProductIdResponse updateShareCounts(@Param("type") String type, @Param("productId") Long productId,
            UpdateShareCountsRequest request);

    /** Request body for {@link #updateShareCounts}. */
    class UpdateShareCountsRequest {

        private String locale;
        private Long totalShares;
        private Long sharesIssued;

        public String getLocale() {
            return locale;
        }

        public UpdateShareCountsRequest locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Long getTotalShares() {
            return totalShares;
        }

        public UpdateShareCountsRequest totalShares(Long totalShares) {
            this.totalShares = totalShares;
            return this;
        }

        public Long getSharesIssued() {
            return sharesIssued;
        }

        public UpdateShareCountsRequest sharesIssued(Long sharesIssued) {
            this.sharesIssued = sharesIssued;
            return this;
        }
    }
}
