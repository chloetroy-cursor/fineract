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
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.ExternalServicesPropertiesData;

/**
 * Feign interface for {@code /v1/externalservice/{servicename}} where the generated {@code ExternalServicesApi} does
 * not match the wire format. Check ExternalServicesConfigurationApiResource for the server-side implementation.
 *
 * <p>
 * {@code GET} answers a JSON array of name/value pairs although the OpenAPI response schema declares a single
 * {@code ExternalServicesPropertiesData}. {@code PUT} accepts any property name of the service as a JSON key (for
 * example {@code s3_access_key}), while the generated {@code PutExternalServiceRequest} only models {@code username}
 * and {@code password}; and its response, undeclared in the spec, is the command result whose {@code changes} map
 * carries the new {@code value}.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface ExternalServicePropertiesApi {

    @RequestLine("GET /v1/externalservice/{serviceName}")
    List<ExternalServicesPropertiesData> retrieveProperties(@Param("serviceName") String serviceName);

    @RequestLine("PUT /v1/externalservice/{serviceName}")
    UpdateExternalServicePropertiesResponse updateProperties(@Param("serviceName") String serviceName, Map<String, String> properties);

    /** Response of {@link #updateProperties}: the command processing result, reduced to the fields the tests read. */
    class UpdateExternalServicePropertiesResponse {

        private Map<String, Object> changes;

        public Map<String, Object> getChanges() {
            return changes;
        }

        public void setChanges(Map<String, Object> changes) {
            this.changes = changes;
        }
    }
}
