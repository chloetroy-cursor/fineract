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

import static org.apache.fineract.client.feign.util.FeignCalls.fail;
import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.DeleteUsersUserIdResponse;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.GetUsersResponse;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostRolesResponse;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PostUsersResponse;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.models.PutUsersUserIdResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.UserCommandsApi.UpdateUserRequest;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;

public final class FeignUserHelper {

    private static final String SIMPLE_USER_PASSWORD = "QwE!5rTy#9uP0";
    private static final String REPAYMENT_LOAN_PERMISSION = "REPAYMENT_LOAN";
    private static final String READ_LOAN_PERMISSION = "READ_LOAN";
    private static final String TEST_USER_EMAIL = "whatever@mifos.org";

    private static FineractFeignClient simpleUserWithoutBypassPermissionClient;

    private FeignUserHelper() {}

    private static FineractFeignClient client() {
        return FineractFeignClientHelper.getFineractFeignClient();
    }

    /**
     * Lazily creates a simple user whose role lacks the loan-checker bypass permission (but can read and repay loans
     * and manage loan reschedules) and returns a Feign client authenticated as that user. The user and client are
     * created once per JVM and reused.
     */
    public static FineractFeignClient getSimpleUserWithoutBypassPermissionClient() {
        if (simpleUserWithoutBypassPermissionClient == null) {
            String username = Utils.uniqueRandomStringGenerator("NonByPassUser", 4);
            createSimpleUser(username);
            simpleUserWithoutBypassPermissionClient = FineractFeignClientHelper.createNewFineractFeignClient(username,
                    SIMPLE_USER_PASSWORD);
        }
        return simpleUserWithoutBypassPermissionClient;
    }

    /**
     * Creates a user in the head office with the given role and staff, a generated username and a server-generated
     * password.
     */
    public static PostUsersResponse createUser(Long roleId, Long staffId) {
        return createUser(roleId, staffId, Utils.uniqueRandomStringGenerator("User_Name_", 3));
    }

    /** Creates a user in the head office with the given role, staff and username and a server-generated password. */
    public static PostUsersResponse createUser(Long roleId, Long staffId, String username) {
        return createUser(baseUserRequest(roleId, staffId, username));
    }

    /**
     * Creates a user the server must refuse, for example one whose username is already taken, and returns the failure.
     */
    public static CallFailedRuntimeException createUserExpectingError(Long roleId, Long staffId, String username) {
        return createUserExpectingError(baseUserRequest(roleId, staffId, username));
    }

    /**
     * Creates a user in the head office with the given role, staff and password; returns the full response so callers
     * can read the generated user id.
     */
    public static PostUsersResponse createUser(Long roleId, Long staffId, String username, String password) {
        return createUser(baseUserRequest(roleId, staffId, username).password(password).repeatPassword(password));
    }

    /**
     * Creates a user from a fully specified request, for callers that need to control the office, roles or name fields
     * the convenience overloads fix.
     */
    public static PostUsersResponse createUser(PostUsersRequest request) {
        return ok(() -> client().users().createUser(request));
    }

    /** Creates a user from a request the server must refuse, for example one with a weak password. */
    public static CallFailedRuntimeException createUserExpectingError(PostUsersRequest request) {
        return fail(() -> client().users().createUser(request));
    }

    /**
     * Builds a creation request for a head-office user with a fresh, permissionless role, a unique username and the
     * given password. The request is not sent; callers pass it to {@link #createUser(PostUsersRequest)} or
     * {@link #createUserExpectingError(PostUsersRequest)}.
     */
    public static PostUsersRequest buildUserRequest(String password) {
        Long roleId = FeignRoleHelper.createRole();
        GetOfficesResponse headOffice = OfficeHelper.getHeadOffice();

        return new PostUsersRequest().username(Utils.uniqueRandomStringGenerator("TestUser", 4)).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email("testuser@example.com").password(password).repeatPassword(password)
                .sendPasswordToEmail(false).officeId(headOffice.getId()).roles(List.of(roleId));
    }

    /** Looks a user up by username in the user list; {@code null} when no user has that name. */
    public static Long getUserId(String username) {
        List<GetUsersResponse> users = ok(() -> client().users().retrieveAllUsers());
        return users.stream().filter(user -> username.equals(user.getUsername())).map(GetUsersResponse::getId).findFirst().orElse(null);
    }

    /** Renames a user, keeping the fixed test name, email and head office; see {@link UserCommandsApi}. */
    public static PutUsersUserIdResponse updateUser(Long userId, String username) {
        return ok(() -> client().create(UserCommandsApi.class).updateUser(userId, updateUserRequest(username)));
    }

    /** Renames a user the server must refuse, for example to a username already taken, and returns the failure. */
    public static CallFailedRuntimeException updateUserExpectingError(Long userId, String username) {
        return fail(() -> client().create(UserCommandsApi.class).updateUser(userId, updateUserRequest(username)));
    }

    public static DeleteUsersUserIdResponse deleteUser(Long userId) {
        return ok(() -> client().users().deleteUser(userId));
    }

    private static PostUsersRequest baseUserRequest(Long roleId, Long staffId, String username) {
        return new PostUsersRequest().username(username).firstname("Test").lastname("User").email(TEST_USER_EMAIL)
                .officeId(OfficeHelper.getHeadOffice().getId()).staffId(staffId).roles(List.of(roleId)).sendPasswordToEmail(false);
    }

    private static UpdateUserRequest updateUserRequest(String username) {
        return new UpdateUserRequest(username, "Test", "User", TEST_USER_EMAIL, OfficeHelper.getHeadOffice().getId());
    }

    private static void createSimpleUser(String username) {
        FineractFeignClient adminClient = client();
        GetOfficesResponse headOffice = OfficeHelper.getHeadOffice();

        PostRolesResponse roleResponse = ok(
                () -> adminClient.roles().createRole(new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("Role_Name_", 5))
                        .description(Utils.randomStringGenerator("Role_Description_", 10))));
        Long roleId = roleResponse.getResourceId();

        Map<String, Boolean> permissions = Map.of(REPAYMENT_LOAN_PERMISSION, true, READ_LOAN_PERMISSION, true, "READ_RESCHEDULELOAN", true,
                "CREATE_RESCHEDULELOAN", true, "REJECT_RESCHEDULELOAN", true, "APPROVE_RESCHEDULELOAN", true);
        ok(() -> adminClient.roles().updateRolePermissions(roleId, new PutRolesRoleIdPermissionsRequest().permissions(permissions)));

        PostUsersResponse userResponse = ok(() -> adminClient.users()
                .createUser(new PostUsersRequest().username(username).firstname(Utils.randomFirstNameGenerator())
                        .lastname(Utils.randomLastNameGenerator()).email(TEST_USER_EMAIL).password(SIMPLE_USER_PASSWORD)
                        .repeatPassword(SIMPLE_USER_PASSWORD).sendPasswordToEmail(false).roles(List.of(roleId))
                        .officeId(headOffice.getId())));
        if (userResponse.getResourceId() == null) {
            throw new IllegalStateException("Failed to create non-bypass user " + username);
        }
    }
}
