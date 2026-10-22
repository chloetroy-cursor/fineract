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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignRoleHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.RoleDetailsApi.RoleDetails;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RolesTest {

    private static final Logger LOG = LoggerFactory.getLogger(RolesTest.class);
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @Test
    public void testCreateRolesStatus() {

        LOG.info("---------------------------------CREATING A ROLE---------------------------------------------");
        final Long roleId = FeignRoleHelper.createRole();
        Assertions.assertNotNull(roleId);

        LOG.info("--------------------------------- Getting ROLE -------------------------------");
        RoleDetails role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());

    }

    @Test
    public void testDisableRolesStatus() {

        LOG.info("---------------------------------CREATING A ROLE---------------------------------------------");
        final Long roleId = FeignRoleHelper.createRole();
        Assertions.assertNotNull(roleId);

        LOG.info("--------------------------------- Getting ROLE -------------------------------");
        RoleDetails role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());

        LOG.info("--------------------------------- DISABLING ROLE -------------------------------");
        final Long disableRoleId = FeignRoleHelper.disableRole(roleId).getResourceId();
        assertEquals(roleId, disableRoleId);
        role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());
        assertTrue(role.getDisabled());

    }

    @Test
    public void testEnableRolesStatus() {

        LOG.info("---------------------------------CREATING A ROLE---------------------------------------------");
        final Long roleId = FeignRoleHelper.createRole();
        Assertions.assertNotNull(roleId);

        LOG.info("--------------------------------- Getting ROLE -------------------------------");
        RoleDetails role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());

        LOG.info("--------------------------------- DISABLING ROLE -------------------------------");
        final Long disableRoleId = FeignRoleHelper.disableRole(roleId).getResourceId();
        assertEquals(roleId, disableRoleId);
        role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());
        assertTrue(role.getDisabled());

        LOG.info("--------------------------------- ENABLING ROLE -------------------------------");
        final Long enableRoleId = FeignRoleHelper.enableRole(roleId).getResourceId();
        assertEquals(roleId, enableRoleId);
        role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());
        assertFalse(role.getDisabled());

    }

    @Test
    public void testDeleteRoleStatus() {

        LOG.info("-------------------------------- CREATING A ROLE---------------------------------------------");
        final Long roleId = FeignRoleHelper.createRole();
        Assertions.assertNotNull(roleId);

        LOG.info("--------------------------------- Getting ROLE -------------------------------");
        RoleDetails role = FeignRoleHelper.getRole(roleId);
        assertEquals(roleId, role.getId());

        LOG.info("--------------------------------- DELETE ROLE -------------------------------");
        final Long deleteRoleId = FeignRoleHelper.deleteRole(roleId).getResourceId();
        assertEquals(roleId, deleteRoleId);
    }

    @Test
    public void testRoleShouldGetDeletedIfNoActiveUserExists() {
        final Long roleId = FeignRoleHelper.createRole();
        Assertions.assertNotNull(roleId);

        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(staffId);

        final Integer userId = UserHelper.createUser(this.requestSpec, this.responseSpec, roleId.intValue(), staffId);
        Assertions.assertNotNull(userId);

        final Integer deletedUserId = UserHelper.deleteUser(this.requestSpec, this.responseSpec, userId);
        Assertions.assertEquals(deletedUserId, userId);

        final Long deletedRoleId = FeignRoleHelper.deleteRole(roleId).getResourceId();
        assertEquals(roleId, deletedRoleId);
    }

    @Test
    public void testRoleShouldNotGetDeletedIfActiveUserExists() {
        final Long roleId = FeignRoleHelper.createRole();
        Assertions.assertNotNull(roleId);

        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(staffId);

        final Integer userId = UserHelper.createUser(this.requestSpec, this.responseSpec, roleId.intValue(), staffId);
        Assertions.assertNotNull(userId);

        final CallFailedRuntimeException deleteFailure = FeignRoleHelper.deleteRoleExpectingError(roleId);
        assertEquals(403, deleteFailure.getStatus());
    }

}
