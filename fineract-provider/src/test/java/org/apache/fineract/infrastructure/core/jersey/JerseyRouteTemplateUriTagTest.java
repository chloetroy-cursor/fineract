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
package org.apache.fineract.infrastructure.core.jersey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.ServletException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * The <code>uri</code> tag of <code>http.server.requests</code> for requests served by Jersey, with everything that
 * takes part in production in place: Spring's {@link ServerHttpObservationFilter} in front of the Jersey servlet, the
 * real {@link JerseyConfig} with its <code>@Provider</code> scan, and a meter registry fed by the observation.
 *
 * <p>
 * The resources are stand-ins that declare the same <code>@Path</code> templates as the loan API.
 */
class JerseyRouteTemplateUriTagTest {

    private static final String METER = "http.server.requests";
    private static final String CONTEXT_PATH = "/fineract-provider";
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("/\\d+(/|$)");

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MockServletContext servletContext = new MockServletContext();
    private AnnotationConfigWebApplicationContext applicationContext;
    private ServerHttpObservationFilter observationFilter;
    private ServletContainer jerseyServlet;

    @BeforeEach
    void setUp() throws ServletException {
        servletContext.setContextPath(CONTEXT_PATH);
        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.register(JerseyConfig.class, JerseyRouteTemplateObservationListener.class, LoansResource.class,
                LoanTransactionsResource.class, NotesResource.class, TrailingSlashResource.class);
        applicationContext.setServletContext(servletContext);
        applicationContext.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);

        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
        observationFilter = new ServerHttpObservationFilter(observationRegistry);

        jerseyServlet = new ServletContainer(applicationContext.getBean(JerseyConfig.class));
        jerseyServlet.init(new MockServletConfig(servletContext, "jerseyServlet"));
    }

    @AfterEach
    void tearDown() {
        jerseyServlet.destroy();
        applicationContext.close();
    }

    @Test
    void twoLoanIdsAreOneSeriesTaggedWithTheRouteTemplate() throws Exception {
        assertEquals(200, send("GET", "/v1/loans/1").getStatus());
        assertEquals(200, send("GET", "/v1/loans/2").getStatus());

        Timer timer = timer("GET", "/api/v1/loans/{loanId}", "200");
        assertEquals(2, timer.count());
        assertEquals("SUCCESS", timer.getId().getTag("outcome"));
        assertEquals(1, timers().size(), "two loan ids must not create two series");
        assertNoUriCarriesARequestedPath("/1", "/2");
    }

    @Test
    void everyMatchedRouteReportsItsOwnTemplate() throws Exception {
        send("GET", "/v1/loans");
        send("GET", "/v1/loans/4711");
        send("GET", "/v1/loans/4711/transactions/99");
        send("GET", "/v1/loans/external-id/7c1f9a56-0e4c-4b6e-9d1b-2f6f0f2d8b11");
        send("POST", "/v1/loans/4711/transactions");
        send("GET", "/v1/loans/4711/notes");

        assertEquals(Set.of("/api/v1/loans", "/api/v1/loans/{loanId}", "/api/v1/loans/{loanId}/transactions/{transactionId}",
                "/api/v1/loans/external-id/{loanExternalId}", "/api/v1/loans/{loanId}/transactions",
                "/api/v1/{resourceType:.+}/{resourceId}/notes"), uriValues());
        assertEquals(6, totalCount());
    }

    @Test
    void theCommandQueryParameterIsNotPartOfTheUri() throws Exception {
        MockHttpServletResponse approve = send("POST", "/v1/loans/4711", "command=approve");
        MockHttpServletResponse disburse = send("POST", "/v1/loans/9302", "command=disburse");

        assertEquals("approve 4711", approve.getContentAsString());
        assertEquals("disburse 9302", disburse.getContentAsString());
        assertEquals(2, timer("POST", "/api/v1/loans/{loanId}", "200").count());
        assertEquals(1, timers().size(), "approval and disbursal share the route, so they share the series");
        assertNoUriCarriesARequestedPath("4711", "9302", "command", "approve", "disburse", "?");
    }

    @Test
    void aMatchedRouteThatAnswers404StillReportsItsTemplate() throws Exception {
        assertEquals(404, send("GET", "/v1/loans/999999").getStatus());

        Timer timer = timer("GET", "/api/v1/loans/{loanId}", "404");
        assertEquals(1, timer.count());
        assertEquals("CLIENT_ERROR", timer.getId().getTag("outcome"));
        assertEquals(1, timers().size());
    }

    @Test
    void pathsThatMatchNoRouteShareTheNotFoundSeries() throws Exception {
        assertEquals(404, send("GET", "/v1/no-such-route/abc123").getStatus());
        assertEquals(404, send("GET", "/v1/no-such-route/def456").getStatus());
        assertEquals(404, send("GET", "/v1/loans/1/no-such-subresource/xyz789").getStatus());

        assertEquals(3, timer("GET", "NOT_FOUND", "404").count());
        assertEquals(1, timers().size(), "unmatched paths must not create a series each");
        assertNoUriCarriesARequestedPath("abc123", "def456", "xyz789", "no-such");
    }

    @Test
    void aVerbTheResourceDoesNotAcceptIsUnknownBecauseJerseyMatchedNoMethod() throws Exception {
        assertEquals(405, send("DELETE", "/v1/loans").getStatus());

        assertEquals(1, timer("DELETE", "UNKNOWN", "405").count());
        assertEquals(1, timers().size());
    }

    @Test
    void aMatchedRouteNeverReportsUnknown() throws Exception {
        send("GET", "/v1/loans");
        send("GET", "/v1/loans/4711");
        send("GET", "/v1/loans/999999");
        send("POST", "/v1/loans/4711", "command=approve");
        send("DELETE", "/v1/loans/4711");
        send("GET", "/v1/trailing/");

        assertFalse(uriValues().contains("UNKNOWN"), "uri values: " + uriValues());
        assertFalse(uriValues().contains("NOT_FOUND"), "uri values: " + uriValues());
        assertEquals(Set.of("/api/v1/loans", "/api/v1/loans/{loanId}", "/api/v1/trailing"), uriValues());
    }

    @Test
    void everyRequestIsExactlyOneSample() throws Exception {
        List<String[]> requests = List.of(new String[] { "GET", "/v1/loans" }, new String[] { "GET", "/v1/loans/1" },
                new String[] { "GET", "/v1/loans/2" }, new String[] { "GET", "/v1/loans/999999" }, new String[] { "POST", "/v1/loans/1" },
                new String[] { "DELETE", "/v1/loans" }, new String[] { "GET", "/v1/no-such-route" },
                new String[] { "GET", "/v1/loans/1/transactions/5" }, new String[] { "GET", "/v1/loans/1/notes" });

        for (String[] request : requests) {
            send(request[0], request[1]);
        }

        assertEquals(requests.size(), totalCount());
    }

    @Test
    void theUriIsRelativeToTheContextPathAndHasNoTrailingSlash() throws Exception {
        send("GET", "/v1/trailing/");
        send("GET", "/v1/loans/4711");

        assertEquals(Set.of("/api/v1/trailing", "/api/v1/loans/{loanId}"), uriValues());
        assertNoUriCarriesARequestedPath(CONTEXT_PATH, "localhost");
    }

    /**
     * The cardinality guard for the request timer: its tag keys are the ones Spring defines, and no key or value
     * identifies a loan, a client or a tenant even when the request does.
     */
    @Test
    void noSeriesCarriesALoanClientOrTenantIdentifier() throws Exception {
        for (String[] loanAndTenant : List.of(new String[] { "4711", "acme-bank" }, new String[] { "9302", "globex-bank" })) {
            MockHttpServletRequest request = request("GET", "/v1/loans/" + loanAndTenant[0], null);
            request.addHeader("Fineract-Platform-TenantId", loanAndTenant[1]);
            request.addParameter("clientId", "8801");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new MockFilterChain(jerseyServlet, observationFilter).doFilter(request, response);
            assertEquals(200, response.getStatus());
        }

        assertEquals(1, timers().size());
        assertEquals(2, timer("GET", "/api/v1/loans/{loanId}", "200").count());
        for (Timer timer : timers()) {
            Set<String> keys = timer.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
            assertEquals(Set.of("method", "uri", "status", "outcome", "exception", "error"), keys, "tag keys of " + timer.getId());
        }
        assertNoUriCarriesARequestedPath("4711", "9302", "8801");
        for (Meter meter : meterRegistry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                for (String identifier : List.of("4711", "9302", "8801", "acme-bank", "globex-bank")) {
                    assertFalse(tag.getValue().contains(identifier), meter.getId() + " carries " + identifier);
                }
            }
        }
    }

    private MockHttpServletResponse send(String method, String pathInfo) throws IOException, ServletException {
        return send(method, pathInfo, null);
    }

    private MockHttpServletResponse send(String method, String pathInfo, String queryString) throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new MockFilterChain(jerseyServlet, observationFilter).doFilter(request(method, pathInfo, queryString), response);
        return response;
    }

    /**
     * A request as the servlet container hands it to a servlet mapped to <code>/api/*</code> in an application deployed
     * at <code>/fineract-provider</code>.
     */
    private MockHttpServletRequest request(String method, String pathInfo, String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext, method,
                CONTEXT_PATH + JerseyConfig.APPLICATION_PATH + pathInfo);
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath(JerseyConfig.APPLICATION_PATH);
        request.setPathInfo(pathInfo);
        request.setQueryString(queryString);
        if (queryString != null) {
            for (String parameter : queryString.split("&", -1)) {
                String[] nameAndValue = parameter.split("=", 2);
                request.addParameter(nameAndValue[0], nameAndValue[1]);
            }
        }
        return request;
    }

    private Collection<Timer> timers() {
        return meterRegistry.find(METER).timers();
    }

    private Timer timer(String method, String uri, String status) {
        Timer timer = meterRegistry.find(METER).tag("method", method).tag("uri", uri).tag("status", status).timer();
        assertNotNull(timer, "no " + METER + " timer for " + method + " " + uri + " " + status + "; uri values: " + uriValues());
        return timer;
    }

    private Set<String> uriValues() {
        return timers().stream().map(timer -> timer.getId().getTag("uri")).collect(Collectors.toSet());
    }

    private long totalCount() {
        return timers().stream().mapToLong(Timer::count).sum();
    }

    private void assertNoUriCarriesARequestedPath(String... fragments) {
        for (String uri : uriValues()) {
            assertFalse(NUMERIC_PATH_SEGMENT.matcher(uri).find(), "uri " + uri + " has a numeric path segment");
            for (String fragment : fragments) {
                assertFalse(uri.contains(fragment), "uri " + uri + " contains " + fragment);
            }
        }
        assertTrue(uriValues().stream().allMatch(uri -> uri.startsWith("/api/") || Set.of("NOT_FOUND", "UNKNOWN").contains(uri)),
                "uri values: " + uriValues());
    }

    @Path("/v1/loans")
    @Produces(MediaType.TEXT_PLAIN)
    public static class LoansResource {

        @GET
        public String retrieveAll() {
            return "all";
        }

        @GET
        @Path("{loanId}")
        public String retrieveOne(@PathParam("loanId") Long loanId) {
            if (loanId == 999999L) {
                throw new NotFoundException();
            }
            return "loan " + loanId;
        }

        @POST
        @Path("{loanId}")
        public String stateTransition(@PathParam("loanId") Long loanId, @QueryParam("command") String command) {
            return command + " " + loanId;
        }

        @DELETE
        @Path("{loanId}")
        public String delete(@PathParam("loanId") Long loanId) {
            return "deleted " + loanId;
        }

        @GET
        @Path("external-id/{loanExternalId}")
        public String retrieveByExternalId(@PathParam("loanExternalId") String loanExternalId) {
            return "loan " + loanExternalId;
        }
    }

    @Path("/v1/loans")
    @Produces(MediaType.TEXT_PLAIN)
    public static class LoanTransactionsResource {

        @POST
        @Path("{loanId}/transactions")
        public String execute(@PathParam("loanId") Long loanId, @QueryParam("command") String command) {
            return command + " on " + loanId;
        }

        @GET
        @Path("{loanId}/transactions/{transactionId}")
        public String retrieve(@PathParam("loanId") Long loanId, @PathParam("transactionId") Long transactionId) {
            return "transaction " + transactionId + " of " + loanId;
        }
    }

    @Path("/v1/{resourceType:.+}/{resourceId}/notes")
    @Produces(MediaType.TEXT_PLAIN)
    public static class NotesResource {

        @GET
        public String retrieveAll(@PathParam("resourceType") String resourceType, @PathParam("resourceId") Long resourceId) {
            return "notes of " + resourceType + " " + resourceId;
        }
    }

    @Path("/v1/trailing/")
    @Produces(MediaType.TEXT_PLAIN)
    public static class TrailingSlashResource {

        @GET
        @Path("/")
        public String retrieveAll() {
            return "trailing";
        }
    }
}
