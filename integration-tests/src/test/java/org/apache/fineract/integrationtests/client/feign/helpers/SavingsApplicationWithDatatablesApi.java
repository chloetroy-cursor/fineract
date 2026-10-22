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
import java.util.Map;
import org.apache.fineract.client.models.PostSavingsAccountsResponse;

/**
 * {@code PostSavingsAccountsRequest} has no {@code datatables} member, so an application that must satisfy an entity
 * datatable check is posted as a plain map.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface SavingsApplicationWithDatatablesApi {

    @RequestLine("POST /v1/savingsaccounts")
    PostSavingsAccountsResponse submitSavingsApplication(Map<String, Object> application);
}
