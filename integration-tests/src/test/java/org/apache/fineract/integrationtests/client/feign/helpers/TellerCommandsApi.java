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
import org.apache.fineract.client.models.PostTellersResponse;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersCashierIdAllocateResponse;

/**
 * Feign interface for the two teller request shapes the generated {@code tellerCashManagement()} API cannot express.
 * Everything else about tellers and cashiers goes through the generated client.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface TellerCommandsApi {

    /**
     * Creates a teller. The generated {@code PostTellersRequest.status} is an enum serialised as {@code "ACTIVE"}, but
     * the server reads {@code status} as the integer code of {@code TellerStatus} ({@code 300} for active), so the
     * generated model cannot create a teller.
     */
    @RequestLine("POST /v1/tellers")
    PostTellersResponse createTeller(CreateTellerRequest request);

    /**
     * Posts the body to the allocate endpoint exactly as given. The JSON encoder passes a {@code String} body through
     * untouched, which is what a test that sends a non-numeric {@code txnAmount} or malformed JSON needs.
     */
    @RequestLine("POST /v1/tellers/{tellerId}/cashiers/{cashierId}/allocate")
    PostTellersTellerIdCashiersCashierIdAllocateResponse allocateCashToCashier(@Param("tellerId") Long tellerId,
            @Param("cashierId") Long cashierId, String rawJsonBody);

    /** Request body for {@link #createTeller}. {@code status} is the integer {@code TellerStatus} code. */
    class CreateTellerRequest {

        private Long officeId;
        private String name;
        private String description;
        private Integer status;
        private String startDate;
        private String locale;
        private String dateFormat;

        public Long getOfficeId() {
            return officeId;
        }

        public CreateTellerRequest officeId(Long officeId) {
            this.officeId = officeId;
            return this;
        }

        public String getName() {
            return name;
        }

        public CreateTellerRequest name(String name) {
            this.name = name;
            return this;
        }

        public String getDescription() {
            return description;
        }

        public CreateTellerRequest description(String description) {
            this.description = description;
            return this;
        }

        public Integer getStatus() {
            return status;
        }

        public CreateTellerRequest status(Integer status) {
            this.status = status;
            return this;
        }

        public String getStartDate() {
            return startDate;
        }

        public CreateTellerRequest startDate(String startDate) {
            this.startDate = startDate;
            return this;
        }

        public String getLocale() {
            return locale;
        }

        public CreateTellerRequest locale(String locale) {
            this.locale = locale;
            return this;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public CreateTellerRequest dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }
    }
}
