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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import feign.Response;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.apache.fineract.client.feign.FineractMultipartEncoder.MultipartData;
import org.apache.fineract.integrationtests.ConfigProperties;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raw HTTP access to the server through the Feign client ({@link RawHttpApi}) for the few calls the generated client
 * cannot express.
 * <p>
 * Paths are resolved against the server under test: a path that starts with {@code /fineract-provider/} (the legacy URL
 * constants) is taken from the server root, anything else is relative to {@code /fineract-provider/api/v1}.
 * <p>
 * The static methods run as the default user and require a 2xx answer. {@link #call(Integer)},
 * {@link #call(String, Integer)} and {@link #anonymous()} give a {@link Call} for another expected status, another
 * user, or no credentials. The JSON-attribute variants read the body the way REST Assured's
 * {@code JsonPath.get(attribute)} did (dotted paths, {@code [n]} indices, list spread; integers as
 * {@link Integer}/{@link Long}, decimals as {@link Float}/{@link Double}, objects as {@link Map}, arrays as
 * {@link List}).
 */
public final class FeignRawHttpHelper {

    private static final Logger LOG = LoggerFactory.getLogger(FeignRawHttpHelper.class);
    private static final String API_V1_PREFIX = "/fineract-provider/api/v1";
    private static final String SERVER_ROOT_PREFIX = "/fineract-provider/";

    /**
     * Both Spring Security and Fineract's tenant filter ignore an {@code Authorization} header that does not start with
     * {@code Basic}, so this value makes a request anonymous while keeping the client's interceptor from filling in the
     * default credentials.
     */
    private static final String NO_CREDENTIALS = "None";

    private static final RawHttpApi API = FineractFeignClientHelper.getFineractFeignClient().create(RawHttpApi.class);
    private static final Call DEFAULT = call((Integer) null);

    private FeignRawHttpHelper() {}

    /** The {@code Authorization} header value for HTTP basic authentication as {@code username}. */
    public static String basicAuthorization(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /** Default user, 2xx expected. */
    public static Call call() {
        return DEFAULT;
    }

    /**
     * Default user.
     *
     * @param expectedStatus
     *            the only accepted HTTP status, or {@code null} for any 2xx status
     */
    public static Call call(Integer expectedStatus) {
        return call(basicAuthorization(ConfigProperties.Backend.USERNAME, ConfigProperties.Backend.PASSWORD), expectedStatus);
    }

    /**
     * @param authorization
     *            the {@code Authorization} header value, see {@link #basicAuthorization(String, String)}
     * @param expectedStatus
     *            the only accepted HTTP status, or {@code null} for any 2xx status
     */
    public static Call call(String authorization, Integer expectedStatus) {
        if (expectedStatus == null) {
            return call(authorization, status -> status >= 200 && status < 300, "2xx");
        }
        return call(authorization, status -> status == expectedStatus, String.valueOf(expectedStatus));
    }

    public static Call call(String authorization, IntPredicate statusAccepted, String expectedStatusDescription) {
        return new Call(API, authorization, statusAccepted, expectedStatusDescription);
    }

    /** Any status is accepted; the caller inspects {@link RawResponse#status()} itself. */
    public static Call callAcceptingAnyStatus(String authorization) {
        return call(authorization, status -> true, "any");
    }

    /** No credentials; any status is accepted so the caller can assert on the 401 or 403 it expects. */
    public static Call anonymous() {
        return callAcceptingAnyStatus(NO_CREDENTIALS);
    }

    public static String get(String path) {
        return DEFAULT.get(path);
    }

    public static <T> T get(String path, String jsonAttribute) {
        return DEFAULT.get(path, jsonAttribute);
    }

    public static byte[] getBytes(String path) {
        return DEFAULT.getBytes(path);
    }

    public static String post(String path, String jsonBody) {
        return DEFAULT.post(path, jsonBody);
    }

    public static <T> T post(String path, String jsonBody, String jsonAttribute) {
        return DEFAULT.post(path, jsonBody, jsonAttribute);
    }

    public static String put(String path, String jsonBody) {
        return DEFAULT.put(path, jsonBody);
    }

    public static <T> T put(String path, String jsonBody, String jsonAttribute) {
        return DEFAULT.put(path, jsonBody, jsonAttribute);
    }

    public static <T> T delete(String path, String jsonAttribute) {
        return DEFAULT.delete(path, jsonAttribute);
    }

    /**
     * Reads {@code jsonAttribute} out of a JSON document with REST Assured {@code JsonPath} semantics. A {@code null}
     * attribute returns the raw document string (as the legacy {@code Utils.performServer*} methods did); {@code ""}
     * and {@code "$"} return the parsed document.
     */
    @SuppressWarnings("unchecked")
    public static <T> T jsonAttribute(String json, String jsonAttribute) {
        if (jsonAttribute == null) {
            return (T) json;
        }
        if (json == null || json.isBlank()) {
            return null;
        }
        Object root = toJavaObject(JsonParser.parseString(json));
        if (jsonAttribute.isEmpty() || "$".equals(jsonAttribute)) {
            return (T) root;
        }
        Object current = root;
        for (String segment : jsonAttribute.split("\\.")) {
            current = step(current, segment, jsonAttribute);
        }
        return (T) current;
    }

    private static Object step(Object current, String segment, String fullPath) {
        String key = segment;
        List<Integer> indices = new ArrayList<>();
        int bracket = segment.indexOf('[');
        if (bracket >= 0) {
            key = segment.substring(0, bracket);
            String rest = segment.substring(bracket);
            for (String index : rest.replace("]", "").split("\\[")) {
                if (!index.isEmpty()) {
                    try {
                        indices.add(Integer.parseInt(index.trim()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Unsupported JSON attribute path: " + fullPath, e);
                    }
                }
            }
        }
        if (key.indexOf('(') >= 0 || key.indexOf('{') >= 0) {
            throw new IllegalArgumentException("Unsupported JSON attribute path: " + fullPath);
        }
        Object value = current;
        if (!key.isEmpty()) {
            value = property(current, key);
        }
        for (Integer index : indices) {
            if (!(value instanceof List<?> list)) {
                return null;
            }
            value = index < list.size() ? list.get(index) : null;
        }
        return value;
    }

    private static Object property(Object current, String key) {
        if (current instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (current instanceof List<?> list) {
            List<Object> projected = new ArrayList<>(list.size());
            for (Object element : list) {
                projected.add(property(element, key));
            }
            return projected;
        }
        return null;
    }

    private static Object toJavaObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), toJavaObject(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(toJavaObject(item));
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return toNumber(primitive.getAsString());
        }
        return primitive.getAsString();
    }

    /**
     * REST Assured's default {@code FLOAT_AND_DOUBLE} number mapping: integers become {@link Integer}, {@link Long} or
     * {@link BigInteger} by size, decimals become {@link Float} when they fit and {@link Double} otherwise.
     */
    private static Number toNumber(String text) {
        if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
            BigDecimal decimal = new BigDecimal(text);
            double asDouble = decimal.doubleValue();
            if (Double.isInfinite(asDouble) || Math.abs(asDouble) > Float.MAX_VALUE) {
                return asDouble;
            }
            return (float) asDouble;
        }
        BigInteger integer = new BigInteger(text);
        if (integer.bitLength() < 32) {
            return integer.intValue();
        }
        if (integer.bitLength() < 64) {
            return integer.longValue();
        }
        return integer;
    }

    static URI resolve(String path) {
        String url;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            url = path;
        } else if (path.startsWith(SERVER_ROOT_PREFIX)) {
            url = serverOrigin() + path;
        } else {
            url = serverOrigin() + API_V1_PREFIX + (path.startsWith("/") ? path : "/" + path);
        }
        int query = url.indexOf('?');
        if (query < 0) {
            return URI.create(percentEncodeIllegalCharacters(url));
        }
        return URI.create(percentEncodeIllegalCharacters(url.substring(0, query)) + "?" + encodeQuery(url.substring(query + 1)));
    }

    /**
     * REST Assured encoded every query parameter name and value itself, so legacy URLs carry raw spaces, quotes,
     * semicolons and the like, and tests rely on the server receiving them verbatim (for instance to reject an
     * {@code order} parameter carrying SQL). A semicolon left unencoded is read as a parameter separator instead.
     */
    private static String encodeQuery(String rawQuery) {
        StringBuilder sb = new StringBuilder(rawQuery.length());
        for (String parameter : rawQuery.split("&")) {
            if (parameter.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            int equals = parameter.indexOf('=');
            if (equals < 0) {
                sb.append(encodeQueryComponent(parameter));
            } else {
                sb.append(encodeQueryComponent(parameter.substring(0, equals))).append('=')
                        .append(encodeQueryComponent(parameter.substring(equals + 1)));
            }
        }
        return sb.toString();
    }

    private static String encodeQueryComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String serverOrigin() {
        String configured = System.getProperty("fineract.it.url");
        if (configured != null) {
            URI uri = URI.create(configured);
            return uri.getScheme() + "://" + uri.getAuthority();
        }
        return ConfigProperties.Backend.PROTOCOL + "://" + ConfigProperties.Backend.HOST + ":" + ConfigProperties.Backend.PORT;
    }

    /** Path part: only characters a URI may never contain are encoded, so a path keeps its structure. */
    private static String percentEncodeIllegalCharacters(String url) {
        StringBuilder sb = new StringBuilder(url.length());
        for (char c : url.toCharArray()) {
            if (c <= ' ' || c >= 0x7F || "\"<>\\^`{|}".indexOf(c) >= 0) {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    sb.append('%').append(String.format("%02X", b & 0xFF));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Status and body of a raw call, for callers that assert on the status themselves. */
    public record RawResponse(int status, byte[] body) {

        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public <T> T jsonAttribute(String jsonAttribute) {
            return FeignRawHttpHelper.jsonAttribute(bodyAsString(), jsonAttribute);
        }
    }

    /** A raw caller bound to one {@code Authorization} header value (one user) and one expected HTTP status. */
    public static final class Call {

        private final RawHttpApi api;
        private final String authorization;
        private final IntPredicate statusAccepted;
        private final String expectedStatusDescription;

        private Call(RawHttpApi api, String authorization, IntPredicate statusAccepted, String expectedStatusDescription) {
            this.api = api;
            this.authorization = authorization;
            this.statusAccepted = statusAccepted;
            this.expectedStatusDescription = expectedStatusDescription;
        }

        /** Status and body of a GET, POST, PUT or DELETE with an optional JSON body. */
        public RawResponse response(String method, String path, String jsonBody) {
            URI uri = resolve(path);
            return switch (method) {
                case "GET" -> exchange(method, path, () -> api.get(uri, authorization));
                case "POST" -> exchange(method, path, () -> api.post(uri, authorization, body(jsonBody)));
                case "PUT" -> exchange(method, path, () -> api.put(uri, authorization, body(jsonBody)));
                case "DELETE" -> exchange(method, path,
                        () -> jsonBody == null ? api.delete(uri, authorization) : api.delete(uri, authorization, jsonBody));
                default -> throw new IllegalArgumentException("Unsupported method " + method);
            };
        }

        public String get(String path) {
            return exchange("GET", path, () -> api.get(resolve(path), authorization)).bodyAsString();
        }

        public <T> T get(String path, String jsonAttribute) {
            return jsonAttribute(get(path), jsonAttribute);
        }

        public byte[] getBytes(String path) {
            return exchange("GET", path, () -> api.getBinary(resolve(path), authorization)).body();
        }

        /**
         * Legacy {@code performServerGetArray}: element {@code position} of the top-level array, attribute
         * {@code jsonAttribute} of it, as a Gson array.
         */
        public JsonArray getArray(String path, int position, String jsonAttribute) {
            JsonElement root = JsonParser.parseString(get(path));
            JsonObject item = root.getAsJsonArray().get(position).getAsJsonObject();
            return item.get(jsonAttribute).getAsJsonArray();
        }

        public String post(String path, String jsonBody) {
            LOG.info("JSON {}", jsonBody);
            return exchange("POST", path, () -> api.post(resolve(path), authorization, body(jsonBody))).bodyAsString();
        }

        public <T> T post(String path, String jsonBody, String jsonAttribute) {
            return jsonAttribute(post(path, jsonBody), jsonAttribute);
        }

        public String put(String path, String jsonBody) {
            return exchange("PUT", path, () -> api.put(resolve(path), authorization, body(jsonBody))).bodyAsString();
        }

        public <T> T put(String path, String jsonBody, String jsonAttribute) {
            return jsonAttribute(put(path, jsonBody), jsonAttribute);
        }

        public <T> T delete(String path, String jsonAttribute) {
            return jsonAttribute(exchange("DELETE", path, () -> api.delete(resolve(path), authorization)).bodyAsString(), jsonAttribute);
        }

        public <T> T delete(String path, String jsonBody, String jsonAttribute) {
            return jsonAttribute(exchange("DELETE", path, () -> api.delete(resolve(path), authorization, body(jsonBody))).bodyAsString(),
                    jsonAttribute);
        }

        /** Feign rejects a {@code null} body parameter; REST Assured simply sent nothing. */
        private static String body(String jsonBody) {
            return jsonBody == null ? "" : jsonBody;
        }

        public String postMultipart(String path, MultipartData multipartData) {
            return exchange("POST", path, () -> api.postMultipart(resolve(path), authorization, multipartData)).bodyAsString();
        }

        /**
         * Bulk import upload: {@code legalFormType} (when given) as query parameter, the workbook plus locale and date
         * format as form parts.
         */
        public String postTemplate(String path, String legalFormType, File file, String locale, String dateFormat) {
            String url = legalFormType == null ? path : path + (path.indexOf('?') >= 0 ? "&" : "?") + "legalFormType=" + legalFormType;
            try {
                MultipartData data = new MultipartData()
                        .addFile("file", file.getName(), Files.readAllBytes(file.toPath()), "application/vnd.ms-excel")
                        .addText("locale", locale).addText("dateFormat", dateFormat);
                return postMultipart(url, data);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + file, e);
            }
        }

        public String getOutputTemplateLocation(String path, String importDocumentId) {
            return get(withImportDocumentId(path, importDocumentId));
        }

        public byte[] getOutputTemplate(String path, String importDocumentId) {
            return getBytes(withImportDocumentId(path, importDocumentId));
        }

        private static String withImportDocumentId(String path, String importDocumentId) {
            return path + (path.indexOf('?') >= 0 ? "&" : "?") + "importDocumentId=" + importDocumentId;
        }

        private RawResponse exchange(String method, String path, java.util.function.Supplier<Response> request) {
            try (Response response = request.get()) {
                byte[] body = response.body() == null ? new byte[0] : response.body().asInputStream().readAllBytes();
                int status = response.status();
                if (!statusAccepted.test(status)) {
                    String text = new String(body, StandardCharsets.UTF_8);
                    LOG.error("Expected HTTP {} but got {} for {} {}: {}", expectedStatusDescription, status, method, path, text);
                    throw new RuntimeException("HTTP " + status + " " + method + " " + path + ": " + text);
                }
                return new RawResponse(status, body);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to execute " + method + " " + path, e);
            }
        }
    }
}
