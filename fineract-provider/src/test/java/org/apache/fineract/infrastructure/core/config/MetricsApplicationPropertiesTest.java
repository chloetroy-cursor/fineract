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
package org.apache.fineract.infrastructure.core.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Meter;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.ServiceLevelObjectiveBoundary;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Reads the defaults of the shipped <code>application.properties</code> the way Spring Boot does and checks the metrics
 * settings the "Metric naming and cardinality" chapter of the architecture documentation requires. Binding to
 * {@link MetricsProperties} also proves that each key is one Spring Boot still reads: a misspelled or renamed key would
 * be ignored at runtime without any error.
 */
class MetricsApplicationPropertiesTest {

    private static MetricsProperties metrics;

    @BeforeAll
    static void bindApplicationProperties() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        // Only the file: the FINERACT_* variables of the machine running the test must not change the outcome
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        metrics = Binder.get(environment).bind("management.metrics", MetricsProperties.class).get();
    }

    @Test
    void theUriTagOfHttpServerRequestsAllowsOneThousandValues() {
        assertEquals(1000, metrics.getWeb().getServer().getMaxUriTags());
    }

    @Test
    void theLoanCommandTimerPublishesItsEightServiceLevelObjectiveBuckets() {
        assertArrayEquals(
                nanos(Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
                        Duration.ofMillis(2500), Duration.ofSeconds(5), Duration.ofSeconds(10)),
                sloNanos("fineract.loan.command.execution"));
    }

    @Test
    void theCobStepTimerPublishesItsEightServiceLevelObjectiveBuckets() {
        assertArrayEquals(nanos(Duration.ofMillis(5), Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(50),
                Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofSeconds(5)),
                sloNanos("fineract.cob.step.execution"));
    }

    @Test
    void noFineractMeterPublishesAPercentileHistogramOrClientSidePercentiles() {
        assertFalse(metrics.getDistribution().getPercentilesHistogram().keySet().stream().anyMatch(name -> name.startsWith("fineract")),
                "percentiles-histogram costs about 70 series for each tag combination");
        assertTrue(metrics.getDistribution().getPercentiles().isEmpty(), "percentiles computed in the application cannot be aggregated");
        assertEquals(false, metrics.getDistribution().getPercentilesHistogram().get("http.server.requests"),
                "the request histogram is off unless a deployment turns it on");
    }

    @Test
    void theOnlyCommonTagIsTheApplication() {
        Map<String, String> commonTags = metrics.getTags();

        assertEquals(Set.of("application"), commonTags.keySet(), "a common tag is added to every series; the tenant must never be one");
        assertEquals("fineract", commonTags.get("application"));
    }

    private static double[] sloNanos(String meterName) {
        ServiceLevelObjectiveBoundary[] boundaries = metrics.getDistribution().getSlo().get(meterName);
        assertNotNull(boundaries, "no management.metrics.distribution.slo." + meterName);
        return Arrays.stream(boundaries).mapToDouble(boundary -> boundary.getValue(Meter.Type.TIMER)).toArray();
    }

    private static double[] nanos(Duration... durations) {
        return Arrays.stream(durations).mapToDouble(Duration::toNanos).toArray();
    }
}
