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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.logging.LoggingContext;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Runs the filter against the real MDC, the way it is registered: once in front of the security filter chain and once
 * inside it.
 */
class CorrelationHeaderFilterMdcTest {

    private final FineractProperties fineractProperties = new FineractProperties();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/loans/42");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private CorrelationHeaderFilter outerFilter;
    private CorrelationHeaderFilter filterInSecurityChain;

    @BeforeEach
    void setUp() throws Exception {
        MDC.clear();
        fineractProperties.setCorrelation(new FineractProperties.FineractCorrelationProperties());
        fineractProperties.getLogging().getJson().setEnabled(true);
        outerFilter = new CorrelationHeaderFilter(fineractProperties, new MDCWrapper());
        // the servlet container names the registered filter; the one in the security chain is never initialised
        outerFilter.init(new MockFilterConfig("correlationHeaderFilterRegistration"));
        filterInSecurityChain = new CorrelationHeaderFilter(fineractProperties, new MDCWrapper());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void theCorrelationIdOfTheHeaderIsInTheMdcDuringTheRequestAndGoneAfterwards() throws Exception {
        request.addHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME, "abc-123");
        final List<String> seenByTheRequest = new ArrayList<>();

        outerFilter.doFilter(request, response, (req, res) -> seenByTheRequest.add(MDC.get(LoggingContext.CORRELATION_ID)));

        assertThat(seenByTheRequest).containsExactly("abc-123");
        assertThat(MDC.get(LoggingContext.CORRELATION_ID)).isNull();
    }

    @Test
    void aRequestWithoutTheHeaderGetsAGeneratedIdThatIsReturnedToTheCaller() throws Exception {
        final List<String> seenByTheRequest = new ArrayList<>();

        outerFilter.doFilter(request, response, (req, res) -> seenByTheRequest.add(MDC.get(LoggingContext.CORRELATION_ID)));

        assertThat(seenByTheRequest).hasSize(1);
        assertThat(UUID.fromString(seenByTheRequest.get(0))).isNotNull();
        assertThat(response.getHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME)).isEqualTo(seenByTheRequest.get(0));
        assertThat(MDC.get(LoggingContext.CORRELATION_ID)).isNull();
    }

    @Test
    void theIdOfOneRequestIsNotSeenByTheNextRequestOnTheSameThread() throws Exception {
        request.addHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME, "first-request");
        final List<String> seenByTheFirstRequest = new ArrayList<>();
        outerFilter.doFilter(request, response, (req, res) -> seenByTheFirstRequest.add(MDC.get(LoggingContext.CORRELATION_ID)));
        assertThat(seenByTheFirstRequest).containsExactly("first-request");

        final List<String> seenByTheNextRequest = new ArrayList<>();
        outerFilter.doFilter(new MockHttpServletRequest("GET", "/api/v1/loans/43"), new MockHttpServletResponse(),
                (req, res) -> seenByTheNextRequest.add(MDC.get(LoggingContext.CORRELATION_ID)));

        assertThat(seenByTheNextRequest).hasSize(1).doesNotContain("first-request");
    }

    @Test
    void theCorrelationIdIsRemovedWhenTheRequestFails() {
        request.addHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME, "abc-123");
        final FilterChain failingChain = (req, res) -> {
            throw new ServletException("request failed");
        };

        assertThatThrownBy(() -> outerFilter.doFilter(request, response, failingChain)).isInstanceOf(ServletException.class);

        assertThat(MDC.get(LoggingContext.CORRELATION_ID)).isNull();
    }

    @Test
    void theInstanceInsideTheSecurityChainDoesNotRemoveTheIdBeforeTheOuterOneIsDone() throws Exception {
        request.addHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME, "abc-123");
        final List<String> duringTheRequest = new ArrayList<>();
        final List<String> afterTheSecurityChain = new ArrayList<>();

        outerFilter.doFilter(request, response, (req, res) -> {
            filterInSecurityChain.doFilter(req, res, (innerReq, innerRes) -> duringTheRequest.add(MDC.get(LoggingContext.CORRELATION_ID)));
            // e.g. the line the tenant filter logs once the response is written
            afterTheSecurityChain.add(MDC.get(LoggingContext.CORRELATION_ID));
        });

        assertThat(duringTheRequest).containsExactly("abc-123");
        assertThat(afterTheSecurityChain).containsExactly("abc-123");
        assertThat(MDC.get(LoggingContext.CORRELATION_ID)).isNull();
    }

    @Test
    void nothingIsPutIntoTheMdcWhenJsonLoggingAndCorrelationAreBothOff() throws Exception {
        fineractProperties.getLogging().getJson().setEnabled(false);
        request.addHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME, "abc-123");
        final List<String> seenByTheRequest = new ArrayList<>();

        outerFilter.doFilter(request, response, (req, res) -> seenByTheRequest.add(MDC.get(LoggingContext.CORRELATION_ID)));

        assertThat(seenByTheRequest).containsExactly((String) null);
        assertThat(response.getHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME)).isNull();
    }
}
