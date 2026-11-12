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
package org.apache.fineract.infrastructure.core.logging;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Puts the loan id of a loan API request into the logging MDC, for every resource method whose path has a
 * {@code {loanId}} parameter.
 * <p>
 * The id stays on the thread until the end of the request, so the lines logged while the response is written carry it
 * too. It is removed by {@code ThreadLocalContextUtil.reset()}, which the tenant filter of every security setup calls
 * when the request is done.
 */
@Provider
@Component
@Scope("singleton")
public class LoanIdLoggingContextFilter implements ContainerRequestFilter {

    static final String LOAN_ID_PATH_PARAMETER = "loanId";
    // Working capital loans are a separate entity with their own ids, which would read as loan ids in a log search.
    static final String WORKING_CAPITAL_LOAN_PATH = "working-capital-loans";
    // 18 digits always fit a long; anything else is not a loan id and must not reach the logs
    private static final Pattern LOAN_ID_PATTERN = Pattern.compile("\\d{1,18}");

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final UriInfo uriInfo = requestContext.getUriInfo();
        final String loanId = uriInfo.getPathParameters().getFirst(LOAN_ID_PATH_PARAMETER);
        if (loanId == null || !LOAN_ID_PATTERN.matcher(loanId).matches() || uriInfo.getPath().contains(WORKING_CAPITAL_LOAN_PATH)) {
            return;
        }
        LoggingContext.setLoanId(Long.valueOf(loanId));
    }
}
