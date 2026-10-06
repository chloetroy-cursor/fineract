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
import java.util.Map;

/**
 * Raw loan lookup for attributes the server emits on a loan but the OpenAPI schema leaves off
 * {@code GetLoansLoanIdResponse} (for example {@code isNPA}). The server rejects such attributes in {@code ?fields=},
 * so the whole loan is read as a map.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface LoanFieldsApi {

    @RequestLine("GET /v1/loans/{loanId}")
    Map<String, Object> retrieveLoanAsMap(@Param("loanId") Long loanId);
}
