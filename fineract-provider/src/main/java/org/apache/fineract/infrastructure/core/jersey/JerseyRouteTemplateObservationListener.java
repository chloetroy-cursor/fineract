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

import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.regex.Pattern;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.uri.UriTemplate;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ServerHttpObservationFilter;

/**
 * Gives the Spring HTTP server observation the Jersey route template of the request, for example
 * <code>/api/v1/loans/{loanId}</code>.
 *
 * <p>
 * <code>http.server.requests</code> is recorded by {@link ServerHttpObservationFilter}, which wraps every servlet
 * request and takes the <code>uri</code> tag from the path pattern of its observation context. Only Spring MVC handler
 * mappings set that pattern, so without this listener every request served by Jersey is tagged
 * <code>uri="UNKNOWN"</code>.
 *
 * <p>
 * Only a matched route sets the pattern, and the pattern is always the template, never the requested path. A request
 * that matches no route keeps the bounded values of the Spring convention (<code>NOT_FOUND</code>,
 * <code>REDIRECTION</code>, <code>UNKNOWN</code>).
 */
@Provider
@Component
public class JerseyRouteTemplateObservationListener implements ApplicationEventListener {

    private static final Pattern REPEATED_SLASHES = Pattern.compile("/{2,}");

    @Override
    public void onEvent(ApplicationEvent event) {
        // only request events are of interest
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return JerseyRouteTemplateObservationListener::recordRouteTemplate;
    }

    private static void recordRouteTemplate(RequestEvent event) {
        if (event.getType() != RequestEvent.Type.REQUEST_MATCHED) {
            return;
        }
        // Jersey request properties are the servlet request attributes, where the filter publishes its context
        ContainerRequest request = event.getContainerRequest();
        Object context = request.getProperty(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE);
        String routeTemplate = routeTemplate(event.getUriInfo());
        if (context instanceof ServerRequestObservationContext observationContext && routeTemplate != null) {
            observationContext.setPathPattern(routeTemplate);
        }
    }

    /**
     * Joins the Jersey application path and the templates of the matched resource class, sub-resource locators and
     * resource method into one route template, relative to the servlet context path.
     *
     * @param uriInfo
     *            the URI information of a matched request
     * @return the route template, or <code>null</code> when nothing was matched
     */
    static String routeTemplate(ExtendedUriInfo uriInfo) {
        List<UriTemplate> matchedTemplates = uriInfo.getMatchedTemplates();
        if (matchedTemplates.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder(JerseyConfig.APPLICATION_PATH);
        // Jersey lists the root resource template last
        for (int i = matchedTemplates.size() - 1; i >= 0; i--) {
            joined.append('/').append(matchedTemplates.get(i).getTemplate());
        }
        String routeTemplate = REPEATED_SLASHES.matcher(joined).replaceAll("/");
        if (routeTemplate.length() > 1 && routeTemplate.endsWith("/")) {
            routeTemplate = routeTemplate.substring(0, routeTemplate.length() - 1);
        }
        return routeTemplate;
    }
}
