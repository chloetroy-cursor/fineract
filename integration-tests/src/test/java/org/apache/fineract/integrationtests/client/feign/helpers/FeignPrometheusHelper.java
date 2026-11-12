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

import feign.Response;
import feign.Util;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.fineract.client.feign.util.FeignCalls;
import org.apache.fineract.integrationtests.ConfigProperties;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;

/**
 * Reads <code>/actuator/prometheus</code> of the server under test and parses the Prometheus text format.
 */
public final class FeignPrometheusHelper {

    private static final Pattern SAMPLE = Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{(.*)})?\\s+(\\S+)(?:\\s+-?\\d+)?$");
    private static final Pattern LABEL = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final ActuatorPrometheusApi ACTUATOR = FineractFeignClientHelper
            .createNewFineractFeignClient(ConfigProperties.Backend.USERNAME, ConfigProperties.Backend.PASSWORD,
                    builder -> builder.baseUrl(applicationRootUrl()))
            .create(ActuatorPrometheusApi.class);

    private FeignPrometheusHelper() {}

    public static Scrape scrape() {
        try (Response response = FeignCalls.ok(ACTUATOR::scrape)) {
            return parse(Util.toString(response.body().asReader(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read /actuator/prometheus", e);
        }
    }

    /**
     * The actuator is served next to <code>/api</code>, so the API base URL of the other helpers is one level too deep.
     */
    private static String applicationRootUrl() {
        String apiUrl = System.getProperty("fineract.it.url", ConfigProperties.Backend.PROTOCOL + "://" + ConfigProperties.Backend.HOST
                + ":" + ConfigProperties.Backend.PORT + "/fineract-provider/api");
        String root = apiUrl.replaceAll("/+$", "");
        if (root.endsWith("/v1")) {
            root = root.substring(0, root.length() - "/v1".length());
        }
        if (root.endsWith("/api")) {
            root = root.substring(0, root.length() - "/api".length());
        }
        return root;
    }

    public static Scrape parse(String body) {
        List<Sample> samples = new ArrayList<>();
        for (String line : body.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            Matcher sample = SAMPLE.matcher(trimmed);
            if (!sample.matches()) {
                throw new IllegalStateException("Not a Prometheus sample line: " + trimmed);
            }
            Map<String, String> labels = new LinkedHashMap<>();
            if (sample.group(2) != null) {
                Matcher label = LABEL.matcher(sample.group(2));
                while (label.find()) {
                    labels.put(label.group(1), unescape(label.group(2)));
                }
            }
            samples.add(new Sample(sample.group(1), Map.copyOf(labels), parseValue(sample.group(3))));
        }
        return new Scrape(List.copyOf(samples));
    }

    private static String unescape(String labelValue) {
        return labelValue.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static double parseValue(String value) {
        return switch (value) {
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            default -> Double.parseDouble(value);
        };
    }

    public record Sample(String name, Map<String, String> labels, double value) {

        public String label(String key) {
            return labels.get(key);
        }

        public boolean hasLabels(Map<String, String> expected) {
            return labels.entrySet().containsAll(expected.entrySet());
        }
    }

    public record Scrape(List<Sample> samples) {

        public List<Sample> named(String name) {
            return samples.stream().filter(sample -> sample.name().equals(name)).toList();
        }

        public List<Sample> matching(Predicate<Sample> filter) {
            return samples.stream().filter(filter).toList();
        }

        public List<Sample> named(String name, Map<String, String> labels) {
            return samples.stream().filter(sample -> sample.name().equals(name) && sample.hasLabels(labels)).toList();
        }

        /**
         * Sum of every series of the metric that carries the given labels; 0 when there is none, which is also what a
         * counter that was never incremented reads as.
         */
        public double sum(String name, Map<String, String> labels) {
            return named(name, labels).stream().mapToDouble(Sample::value).sum();
        }

        public Set<String> labelValues(String name, String label) {
            return named(name).stream().map(sample -> sample.label(label)).filter(value -> value != null).collect(Collectors.toSet());
        }
    }
}
