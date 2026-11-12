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
import feign.RequestLine;
import org.apache.fineract.client.models.InteropTransactionRequestData;
import org.apache.fineract.client.models.InteropTransactionRequestResponseData;
import org.apache.fineract.client.models.InteropTransactionTypeData;
import org.apache.fineract.client.models.MoneyData;

/**
 * Feign interface for the one interoperation request shape the generated client cannot express. Everything else goes
 * through the generated {@code fineractClient.interOperation()} API.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface InteropCommandsApi {

    /**
     * Creates a transaction request whose {@code expiration} is a date-time <em>string</em> that the server is asked to
     * parse with the {@code dateFormat} and {@code locale} sent alongside it.
     *
     * <p>
     * {@code InteropTransactionRequestData.expiration} is generated as an {@code OffsetDateTime}, and the generated
     * model has no {@code dateFormat} or {@code locale} property: the server reads those straight from the JSON rather
     * than from the DTO. A test that exercises the server's date parsing therefore needs its own request model.
     */
    @RequestLine("POST /v1/interoperation/requests")
    InteropTransactionRequestResponseData createTransactionRequestWithFormattedExpiration(FormattedExpirationTransactionRequest request);

    /**
     * Request body for {@link #createTransactionRequestWithFormattedExpiration}. Reuses the generated {@link MoneyData}
     * and {@link InteropTransactionTypeData} models; only {@code expiration}, {@code dateFormat} and {@code locale}
     * differ from {@link InteropTransactionRequestData}.
     */
    class FormattedExpirationTransactionRequest {

        private String transactionCode;
        private String requestCode;
        private String accountId;
        private InteropTransactionRequestData.TransactionRoleEnum transactionRole;
        private MoneyData amount;
        private InteropTransactionTypeData transactionType;
        private String expiration;
        private String dateFormat;
        private String locale;

        public String getTransactionCode() {
            return transactionCode;
        }

        public FormattedExpirationTransactionRequest transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public String getRequestCode() {
            return requestCode;
        }

        public FormattedExpirationTransactionRequest requestCode(String requestCode) {
            this.requestCode = requestCode;
            return this;
        }

        public String getAccountId() {
            return accountId;
        }

        public FormattedExpirationTransactionRequest accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public InteropTransactionRequestData.TransactionRoleEnum getTransactionRole() {
            return transactionRole;
        }

        public FormattedExpirationTransactionRequest transactionRole(InteropTransactionRequestData.TransactionRoleEnum transactionRole) {
            this.transactionRole = transactionRole;
            return this;
        }

        public MoneyData getAmount() {
            return amount;
        }

        public FormattedExpirationTransactionRequest amount(MoneyData amount) {
            this.amount = amount;
            return this;
        }

        public InteropTransactionTypeData getTransactionType() {
            return transactionType;
        }

        public FormattedExpirationTransactionRequest transactionType(InteropTransactionTypeData transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public String getExpiration() {
            return expiration;
        }

        public FormattedExpirationTransactionRequest expiration(String expiration) {
            this.expiration = expiration;
            return this;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public FormattedExpirationTransactionRequest dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        public String getLocale() {
            return locale;
        }

        public FormattedExpirationTransactionRequest locale(String locale) {
            this.locale = locale;
            return this;
        }
    }
}
