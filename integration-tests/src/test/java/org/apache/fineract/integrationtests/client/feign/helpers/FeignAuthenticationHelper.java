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

import org.apache.fineract.client.models.PostAuthenticationRequest;
import org.apache.fineract.client.models.PostAuthenticationResponse;
import org.apache.fineract.integrationtests.ConfigProperties;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;

/**
 * Logs in through {@code POST /v1/authentication}. The returned key is what the legacy tests put into their
 * {@code Authorization: Basic} header, so a wrong password fails here, at setup time, as it did before.
 */
public final class FeignAuthenticationHelper {

    private FeignAuthenticationHelper() {}

    public static PostAuthenticationResponse login(String username, String password) {
        PostAuthenticationRequest request = new PostAuthenticationRequest().username(username).password(password);
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().authenticationHttpBasic().authenticate(request));
    }

    public static String base64EncodedAuthenticationKey() {
        return base64EncodedAuthenticationKey(ConfigProperties.Backend.USERNAME, ConfigProperties.Backend.PASSWORD);
    }

    public static String base64EncodedAuthenticationKey(String username, String password) {
        String key = login(username, password).getBase64EncodedAuthenticationKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Login as " + username + " returned no base64EncodedAuthenticationKey");
        }
        return key;
    }
}
