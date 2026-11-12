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
import feign.Response;

/**
 * Feign interface for the Prometheus scrape endpoint of Spring Boot Actuator. It is not part of the OpenAPI document,
 * so the generated client has no method for it, and it is served next to <code>/api</code>, not under it: the client
 * that creates this interface must have the application root as its base URL.
 */
@Headers("Accept: text/plain")
public interface ActuatorPrometheusApi {

    @RequestLine("GET /actuator/prometheus")
    Response scrape();
}
