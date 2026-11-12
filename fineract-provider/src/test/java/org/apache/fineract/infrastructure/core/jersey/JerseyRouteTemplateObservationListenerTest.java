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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.List;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * The listener on its own: which Jersey event sets the path pattern of the HTTP server observation, and what the
 * pattern is. {@link JerseyRouteTemplateUriTagTest} covers the same listener behind the real filter and servlet.
 */
class JerseyRouteTemplateObservationListenerTest {

    private final JerseyRouteTemplateObservationListener underTest = new JerseyRouteTemplateObservationListener();
    private final ServerRequestObservationContext observationContext = new ServerRequestObservationContext(
            new MockHttpServletRequest("GET", "/fineract-provider/api/v1/loans/4711"), new MockHttpServletResponse());

    @Test
    void theApplicationPathIsApi() {
        assertEquals("/api", JerseyConfig.APPLICATION_PATH);
        assertEquals("/api", JerseyConfig.class.getAnnotation(ApplicationPath.class).value());
    }

    @Test
    void theListenerIsFoundByTheProviderScanOfJerseyConfig() {
        assertNotNull(JerseyRouteTemplateObservationListener.class.getAnnotation(Provider.class),
                "JerseyConfig registers the beans annotated with @Provider");
        assertNotNull(JerseyRouteTemplateObservationListener.class.getAnnotation(Component.class));
    }

    @Test
    void aMatchedRequestSetsTheRouteTemplateAsPathPattern() {
        // Jersey lists the matched templates innermost first: the resource method, then the resource class
        fire(RequestEvent.Type.REQUEST_MATCHED, observationContext, "/{loanId}", "/v1/loans");

        assertEquals("/api/v1/loans/{loanId}", observationContext.getPathPattern());
    }

    @ParameterizedTest
    @CsvSource({
            // class template, method template, expected
            "/v1/loans, /{loanId}, /api/v1/loans/{loanId}", //
            "/v1/loans, {loanId}, /api/v1/loans/{loanId}", //
            "v1/loans, {loanId}, /api/v1/loans/{loanId}", //
            "/v1/loans/, /, /api/v1/loans", //
            "/v1/loans/, /{loanId}/, /api/v1/loans/{loanId}", //
            "/v1/loans, {loanId}/transactions/{transactionId}, /api/v1/loans/{loanId}/transactions/{transactionId}", //
            "/v1/loans, external-id/{loanExternalId}, /api/v1/loans/external-id/{loanExternalId}", //
            "/v1/{resourceType:.+}/{resourceId}/notes, /{noteId}, /api/v1/{resourceType:.+}/{resourceId}/notes/{noteId}" })
    void theTemplatesAreJoinedWithSingleSlashesAndNoTrailingSlash(String classTemplate, String methodTemplate, String expected) {
        assertEquals(expected, JerseyRouteTemplateObservationListener.routeTemplate(uriInfo(methodTemplate, classTemplate)));
    }

    @Test
    void aResourceMethodWithoutItsOwnPathReportsTheClassTemplate() {
        assertEquals("/api/v1/loans", JerseyRouteTemplateObservationListener.routeTemplate(uriInfo("/v1/loans")));
    }

    @Test
    void subResourceLocatorsAreJoinedOutermostFirst() {
        assertEquals("/api/v1/clients/{clientId}/identifiers/{identifierId}",
                JerseyRouteTemplateObservationListener.routeTemplate(uriInfo("/{identifierId}", "/{clientId}/identifiers", "/v1/clients")));
    }

    @Test
    void theRequestedPathIsNeverPartOfThePattern() {
        ExtendedUriInfo uriInfo = uriInfo("/{loanId}", "/v1/loans");
        when(uriInfo.getPath()).thenReturn("v1/loans/4711");

        fire(RequestEvent.Type.REQUEST_MATCHED, observationContext, uriInfo);

        assertEquals("/api/v1/loans/{loanId}", observationContext.getPathPattern());
    }

    @ParameterizedTest
    @EnumSource(value = RequestEvent.Type.class, mode = EnumSource.Mode.EXCLUDE, names = "REQUEST_MATCHED")
    void everyOtherEventLeavesThePathPatternUnset(RequestEvent.Type type) {
        RequestEvent event = mock(RequestEvent.class);
        when(event.getType()).thenReturn(type);
        ContainerRequest request = mock(ContainerRequest.class);
        when(event.getContainerRequest()).thenReturn(request);

        underTest.onRequest(mock(RequestEvent.class)).onEvent(event);

        assertNull(observationContext.getPathPattern());
        verifyNoInteractions(request);
    }

    @Test
    void aMatchWithoutTemplatesLeavesThePathPatternUnset() {
        fire(RequestEvent.Type.REQUEST_MATCHED, observationContext);

        assertNull(observationContext.getPathPattern());
    }

    @Test
    void aRequestWithoutAnObservationContextIsIgnored() {
        assertDoesNotThrow(() -> fire(RequestEvent.Type.REQUEST_MATCHED, null, "/{loanId}", "/v1/loans"));
    }

    @Test
    void aRequestPropertyOfAnotherTypeIsIgnored() {
        assertDoesNotThrow(() -> fire(RequestEvent.Type.REQUEST_MATCHED, "not an observation context", "/{loanId}", "/v1/loans"));
    }

    @Test
    void applicationEventsAreIgnored() {
        ApplicationEvent event = mock(ApplicationEvent.class);

        assertDoesNotThrow(() -> underTest.onEvent(event));

        verifyNoInteractions(event);
    }

    private void fire(RequestEvent.Type type, Object observationContextProperty, String... templatesInnermostFirst) {
        fire(type, observationContextProperty, uriInfo(templatesInnermostFirst));
    }

    private void fire(RequestEvent.Type type, Object observationContextProperty, ExtendedUriInfo uriInfo) {
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getProperty(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE)).thenReturn(observationContextProperty);
        RequestEvent event = mock(RequestEvent.class);
        when(event.getType()).thenReturn(type);
        when(event.getContainerRequest()).thenReturn(request);
        when(event.getUriInfo()).thenReturn(uriInfo);

        underTest.onRequest(mock(RequestEvent.class)).onEvent(event);
    }

    private static ExtendedUriInfo uriInfo(String... templatesInnermostFirst) {
        ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        List<UriTemplate> templates = Arrays.stream(templatesInnermostFirst).map(UriTemplate::new).toList();
        when(uriInfo.getMatchedTemplates()).thenReturn(templates);
        return uriInfo;
    }
}
