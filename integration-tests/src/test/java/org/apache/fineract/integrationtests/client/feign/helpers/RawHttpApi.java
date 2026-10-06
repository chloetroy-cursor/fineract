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
import feign.Response;
import java.net.URI;
import org.apache.fineract.client.feign.FineractMultipartEncoder.MultipartData;

/**
 * Feign interface for calls the generated client cannot express: arbitrary URLs with raw JSON or multipart bodies,
 * binary downloads, and non-API paths such as the actuator or Swagger UI.
 * <p>
 * Every method takes the full {@link URI} to call, which Feign uses instead of the client's base URL, and the value of
 * the {@code Authorization} header. The client's {@code BasicAuthRequestInterceptor} only fills that header in when it
 * is absent, so this is how a request runs as another user, or with no usable credentials at all. Returning
 * {@link Response} keeps Feign from decoding the body or raising on non-2xx statuses, so the caller decides what a
 * given status means. The tenant header still comes from the {@code FineractFeignClient} the proxy was created from.
 */
public interface RawHttpApi {

    @RequestLine("GET")
    @Headers("Authorization: {authorization}")
    Response get(URI uri, @Param("authorization") String authorization);

    @RequestLine("POST")
    @Headers({ "Content-Type: application/json", "Authorization: {authorization}" })
    Response post(URI uri, @Param("authorization") String authorization, String jsonBody);

    @RequestLine("POST")
    @Headers("Authorization: {authorization}")
    Response postMultipart(URI uri, @Param("authorization") String authorization, MultipartData multipartData);

    @RequestLine("PUT")
    @Headers({ "Content-Type: application/json", "Authorization: {authorization}" })
    Response put(URI uri, @Param("authorization") String authorization, String jsonBody);

    @RequestLine("DELETE")
    @Headers("Authorization: {authorization}")
    Response delete(URI uri, @Param("authorization") String authorization);

    @RequestLine("DELETE")
    @Headers({ "Content-Type: application/json", "Authorization: {authorization}" })
    Response delete(URI uri, @Param("authorization") String authorization, String jsonBody);
}
