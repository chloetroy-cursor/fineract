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
import org.apache.fineract.client.models.PutUsersUserIdResponse;

/**
 * Feign interface for {@code PUT /v1/users/{userId}} with a {@code username}. The server accepts a new username on
 * update ({@code UserDataValidator.UPDATE_SUPPORTED_PARAMETERS} lists it, and the duplicate username check runs on it),
 * but {@code UsersApiResourceSwagger.PutUsersUserIdRequest} does not declare the field, so the generated
 * {@code PutUsersUserIdRequest} cannot carry it.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface UserCommandsApi {

    @RequestLine("PUT /v1/users/{userId}")
    PutUsersUserIdResponse updateUser(@Param("userId") Long userId, UpdateUserRequest request);

    class UpdateUserRequest {

        private final String username;
        private final String firstname;
        private final String lastname;
        private final String email;
        private final Long officeId;

        public UpdateUserRequest(String username, String firstname, String lastname, String email, Long officeId) {
            this.username = username;
            this.firstname = firstname;
            this.lastname = lastname;
            this.email = email;
            this.officeId = officeId;
        }

        public String getUsername() {
            return username;
        }

        public String getFirstname() {
            return firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public String getEmail() {
            return email;
        }

        public Long getOfficeId() {
            return officeId;
        }
    }
}
