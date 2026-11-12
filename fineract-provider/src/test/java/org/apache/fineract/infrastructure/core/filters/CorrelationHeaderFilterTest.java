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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorrelationHeaderFilterTest {

    private static final String CORRELATION_ID_HEADER_NAME = "X-CORR-ID";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private MDCWrapper mdcWrapper;

    private FineractProperties fineractProperties;

    private CorrelationHeaderFilter underTest;

    @BeforeEach
    void setUp() {
        fineractProperties = new FineractProperties();
        FineractProperties.FineractCorrelationProperties correlationProps = new FineractProperties.FineractCorrelationProperties();
        correlationProps.setHeaderName(CORRELATION_ID_HEADER_NAME);
        correlationProps.setEnabled(true);
        fineractProperties.setCorrelation(correlationProps);
        underTest = new CorrelationHeaderFilter(fineractProperties, mdcWrapper);
    }

    @Test
    public void testDoFilterInternalShouldPutCorrelationIdIntoMDCIfHeaderIsPresentAndEnabled() throws Exception {
        // given
        String correlationId = UUID.randomUUID().toString();
        given(request.getHeader(fineractProperties.getCorrelation().getHeaderName())).willReturn(correlationId);

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verify(mdcWrapper).put(CorrelationHeaderFilter.CORRELATION_ID_KEY, correlationId);
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternalShouldNotPutCorrelationIdIntoMDCIfHeaderIsNotPresentAndEnabled() throws Exception {
        // given
        given(request.getHeader(fineractProperties.getCorrelation().getHeaderName())).willReturn(null);

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verify(mdcWrapper, never()).put(anyString(), anyString());
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternalShouldNotPutCorrelationIdIntoMDCIfHeaderIsPresentButWhitespacesAndEnabled() throws Exception {
        // given
        given(request.getHeader(fineractProperties.getCorrelation().getHeaderName())).willReturn("    ");

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verify(mdcWrapper, never()).put(anyString(), anyString());
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternalShouldNotPutCorrelationIdIntoMDCIfHeaderIsPresentAndDisabled() throws Exception {
        // given
        fineractProperties.getCorrelation().setEnabled(false);
        String correlationId = UUID.randomUUID().toString();
        given(request.getHeader(fineractProperties.getCorrelation().getHeaderName())).willReturn(correlationId);

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verifyNoInteractions(mdcWrapper);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternalShouldPutCorrelationIdIntoMDCIfHeaderIsPresentAndOnlyJsonLoggingIsEnabled() throws Exception {
        // given
        fineractProperties.getCorrelation().setEnabled(false);
        fineractProperties.getLogging().getJson().setEnabled(true);
        String correlationId = UUID.randomUUID().toString();
        given(request.getHeader(CORRELATION_ID_HEADER_NAME)).willReturn(correlationId);

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verify(mdcWrapper).put(CorrelationHeaderFilter.CORRELATION_ID_KEY, correlationId);
        verify(response, never()).setHeader(anyString(), anyString());
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void testDoFilterInternalShouldGenerateCorrelationIdIfHeaderIsNotPresentAndJsonLoggingIsEnabled() throws Exception {
        // given
        fineractProperties.getLogging().getJson().setEnabled(true);
        given(request.getHeader(CORRELATION_ID_HEADER_NAME)).willReturn(null);

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        ArgumentCaptor<String> generatedId = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(mdcWrapper, filterChain);
        inOrder.verify(mdcWrapper).put(eq(CorrelationHeaderFilter.CORRELATION_ID_KEY), generatedId.capture());
        inOrder.verify(filterChain).doFilter(request, response);
        inOrder.verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
        assertThat(UUID.fromString(generatedId.getValue())).isNotNull();
        // the caller gets the generated id back, and an error dispatch of the same request finds it again
        verify(response).setHeader(CORRELATION_ID_HEADER_NAME, generatedId.getValue());
        verify(request).setAttribute(CorrelationHeaderFilter.GENERATED_CORRELATION_ID_ATTRIBUTE, generatedId.getValue());
    }

    @Test
    public void testDoFilterInternalShouldReuseTheCorrelationIdGeneratedEarlierForTheSameRequest() throws Exception {
        // given
        fineractProperties.getLogging().getJson().setEnabled(true);
        given(request.getHeader(CORRELATION_ID_HEADER_NAME)).willReturn(null);
        given(request.getAttribute(CorrelationHeaderFilter.GENERATED_CORRELATION_ID_ATTRIBUTE)).willReturn("generated-earlier");

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verify(mdcWrapper).put(CorrelationHeaderFilter.CORRELATION_ID_KEY, "generated-earlier");
        verify(response).setHeader(CORRELATION_ID_HEADER_NAME, "generated-earlier");
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
    }

    @Test
    public void testDoFilterInternalShouldRemoveCorrelationIdFromMDCIfTheRequestFails() throws Exception {
        // given
        fineractProperties.getLogging().getJson().setEnabled(true);
        given(request.getHeader(CORRELATION_ID_HEADER_NAME)).willReturn(null);
        willThrow(new ServletException("request failed")).given(filterChain).doFilter(request, response);

        // when
        assertThatThrownBy(() -> underTest.doFilterInternal(request, response, filterChain)).isInstanceOf(ServletException.class);

        // then
        verify(mdcWrapper).put(eq(CorrelationHeaderFilter.CORRELATION_ID_KEY), anyString());
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
    }

    @Test
    public void testDoFilterInternalShouldReadTheDefaultHeaderIfNoHeaderNameIsConfigured() throws Exception {
        // given
        fineractProperties.getCorrelation().setEnabled(false);
        fineractProperties.getCorrelation().setHeaderName(null);
        fineractProperties.getLogging().getJson().setEnabled(true);
        given(request.getHeader(CorrelationHeaderFilter.DEFAULT_HEADER_NAME)).willReturn("abc-123");

        // when
        underTest.doFilterInternal(request, response, filterChain);

        // then
        verify(mdcWrapper).put(CorrelationHeaderFilter.CORRELATION_ID_KEY, "abc-123");
        verify(mdcWrapper).remove(CorrelationHeaderFilter.CORRELATION_ID_KEY);
    }
}
