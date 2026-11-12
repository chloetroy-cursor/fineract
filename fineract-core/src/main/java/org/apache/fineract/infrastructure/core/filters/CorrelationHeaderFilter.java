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

package org.apache.fineract.infrastructure.core.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.logging.LoggingContext;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.apache.fineract.infrastructure.security.utils.LogParameterEscapeUtil;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the correlation id of an API request into the logging MDC for the duration of the request.
 * <p>
 * Active when {@code fineract.correlation.enabled} or {@code fineract.logging.json.enabled} is set. With JSON logging
 * on, a request that carries no correlation header gets a generated id, which is also returned in the response header
 * so the caller can quote it.
 */
@RequiredArgsConstructor
@Slf4j
public class CorrelationHeaderFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_KEY = LoggingContext.CORRELATION_ID;
    public static final String DEFAULT_HEADER_NAME = "X-Correlation-ID";
    // An error dispatch runs the filter again for the same request; it must see the id generated the first time.
    static final String GENERATED_CORRELATION_ID_ATTRIBUTE = CorrelationHeaderFilter.class.getName() + ".generatedCorrelationId";
    static final String ALREADY_FILTERED_ATTRIBUTE = CorrelationHeaderFilter.class.getName() + ALREADY_FILTERED_SUFFIX;

    private final FineractProperties fineractProperties;
    private final MDCWrapper mdcWrapper;

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws IOException, ServletException {
        FineractProperties.FineractCorrelationProperties correlationProperties = fineractProperties.getCorrelation();
        boolean jsonLoggingEnabled = fineractProperties.getLogging().getJson().isEnabled();
        if (correlationProperties.isEnabled() || jsonLoggingEnabled) {
            handleCorrelations(request, response, filterChain, correlationProperties, jsonLoggingEnabled);
        } else {
            filterChain.doFilter(request, response);
        }

    }

    private void handleCorrelations(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
            FineractProperties.FineractCorrelationProperties correlationProperties, boolean generateIfMissing)
            throws IOException, ServletException {
        try {
            String correlationHeaderName = StringUtils.defaultIfBlank(correlationProperties.getHeaderName(), DEFAULT_HEADER_NAME);
            String correlationId = request.getHeader(correlationHeaderName);
            if (StringUtils.isNotBlank(correlationId)) {
                String escapedCorrelationId = LogParameterEscapeUtil.escapeLogMDCParameter(correlationId);
                mdcWrapper.put(CORRELATION_ID_KEY, escapedCorrelationId);
                log.debug("Found correlationId in header : {}", escapedCorrelationId);
            } else if (generateIfMissing) {
                String generatedCorrelationId = (String) request.getAttribute(GENERATED_CORRELATION_ID_ATTRIBUTE);
                if (generatedCorrelationId == null) {
                    generatedCorrelationId = UUID.randomUUID().toString();
                    request.setAttribute(GENERATED_CORRELATION_ID_ATTRIBUTE, generatedCorrelationId);
                }
                response.setHeader(correlationHeaderName, generatedCorrelationId);
                mdcWrapper.put(CORRELATION_ID_KEY, generatedCorrelationId);
            }
            filterChain.doFilter(request, response);
        } finally {
            mdcWrapper.remove(CORRELATION_ID_KEY);
        }
    }

    // The filter is registered in front of the security filter chain and again inside it. With one marker for both, the
    // inner instance steps aside once the outer one has run, instead of removing the id before the request is over.
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return ALREADY_FILTERED_ATTRIBUTE;
    }

    @Override
    protected boolean isAsyncDispatch(final HttpServletRequest request) {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

}
