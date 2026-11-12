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

import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.ExternalServicesPropertiesData;
import org.apache.fineract.integrationtests.client.feign.helpers.ExternalServicePropertiesApi.UpdateExternalServicePropertiesResponse;

public class FeignExternalServicesConfigurationHelper {

    private final ExternalServicePropertiesApi externalServicePropertiesApi;

    public FeignExternalServicesConfigurationHelper(FineractFeignClient fineractClient) {
        this.externalServicePropertiesApi = fineractClient.create(ExternalServicePropertiesApi.class);
    }

    /** Every property of the service, for example {@code S3}, {@code SMTP} or {@code NOTIFICATION}. */
    public List<ExternalServicesPropertiesData> getExternalServiceProperties(String serviceName) {
        return ok(() -> externalServicePropertiesApi.retrieveProperties(serviceName));
    }

    /** Sets one property of the service and returns the {@code changes} map of the command result. */
    public Map<String, Object> updateExternalServiceProperty(String serviceName, String name, String value) {
        UpdateExternalServicePropertiesResponse response = ok(
                () -> externalServicePropertiesApi.updateProperties(serviceName, Map.of(name, value)));
        return response.getChanges();
    }
}
