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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.ExternalServicesPropertiesData;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignExternalServicesConfigurationHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalServicesConfigurationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalServicesConfigurationTest.class);

    private final FeignExternalServicesConfigurationHelper externalServicesConfigurationHelper = new FeignExternalServicesConfigurationHelper(
            FineractFeignClientHelper.getFineractFeignClient());

    @Test
    public void testExternalServicesConfiguration() {
        updateAndRestoreProperty("S3", "s3_access_key");
        updateAndRestoreProperty("SMTP", "username");

        // Checking for Notifications: the server key is secret, so the API only ever returns it masked
        List<ExternalServicesPropertiesData> notificationConfig = externalServicesConfigurationHelper
                .getExternalServiceProperties("NOTIFICATION");
        assertNotNull(notificationConfig);
        for (ExternalServicesPropertiesData config : notificationConfig) {
            if ("server_key".equals(config.getName())) {
                String value = config.getValue() == null ? "testnull" : config.getValue();
                LOG.info("{} : {}", config.getName(), value);
                assertTrue(hasMoreThanThreeStars(value));
            }
        }
    }

    private void updateAndRestoreProperty(String serviceName, String configName) {
        List<ExternalServicesPropertiesData> externalServicesConfig = externalServicesConfigurationHelper
                .getExternalServiceProperties(serviceName);
        assertNotNull(externalServicesConfig);
        for (ExternalServicesPropertiesData config : externalServicesConfig) {
            if (configName.equals(config.getName())) {
                String value = config.getValue() == null ? "testnull" : config.getValue();
                String newValue = "test";
                LOG.info("{} : {}", config.getName(), value);
                Map<String, Object> changes = externalServicesConfigurationHelper.updateExternalServiceProperty(serviceName, configName,
                        newValue);
                assertNotNull(changes.get("value"));
                assertEquals(newValue, changes.get("value"));
                Map<String, Object> restoredChanges = externalServicesConfigurationHelper.updateExternalServiceProperty(serviceName,
                        configName, value);
                assertNotNull(restoredChanges.get("value"));
                assertEquals(value, restoredChanges.get("value"));
            }
        }
    }

    private boolean hasMoreThanThreeStars(String input) {
        return input != null && input.matches("(.*\\*.*){4,}");
    }
}
