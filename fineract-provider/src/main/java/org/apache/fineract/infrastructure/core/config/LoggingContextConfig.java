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

import org.apache.fineract.infrastructure.core.filters.CorrelationHeaderFilter;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingContextConfig {

    /**
     * Runs the correlation filter in front of the Spring Security filter chain, so the correlation id is on the lines
     * logged while the tenant is resolved and the caller is authenticated, and on requests that are rejected there.
     * <p>
     * The security chains still add their own instance further in, where other filters are ordered relative to it; that
     * instance does nothing once this one has run.
     */
    @Bean
    public FilterRegistrationBean<CorrelationHeaderFilter> correlationHeaderFilterRegistration(final FineractProperties fineractProperties,
            final MDCWrapper mdcWrapper) {
        final FilterRegistrationBean<CorrelationHeaderFilter> registration = new FilterRegistrationBean<>(
                new CorrelationHeaderFilter(fineractProperties, mdcWrapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
