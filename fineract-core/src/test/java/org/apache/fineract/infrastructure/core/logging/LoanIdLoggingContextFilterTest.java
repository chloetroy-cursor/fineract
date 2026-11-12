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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanIdLoggingContextFilterTest {

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private UriInfo uriInfo;

    private final LoanIdLoggingContextFilter underTest = new LoanIdLoggingContextFilter();

    @BeforeEach
    void setUp() {
        MDC.clear();
        given(requestContext.getUriInfo()).willReturn(uriInfo);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void putsTheLoanIdOfALoanRequestIntoTheMdc() {
        givenRequest("v1/loans/42/transactions", "loanId", "42");

        underTest.filter(requestContext);

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isEqualTo("42");
    }

    @Test
    void theLoanIdIsRemovedWhenTheRequestContextIsReset() {
        givenRequest("v1/loans/42", "loanId", "42");
        underTest.filter(requestContext);

        // what the tenant filters do in their finally block once the request is done
        ThreadLocalContextUtil.reset();

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void leavesTheMdcAloneWhenThePathHasNoLoanId() {
        givenRequest("v1/clients/7", "clientId", "7");

        underTest.filter(requestContext);

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void ignoresALoanIdThatIsNotANumber() {
        givenRequest("v1/loans/template", "loanId", "template\n{\"loanId\":\"1\"}");

        underTest.filter(requestContext);

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void ignoresALoanIdThatDoesNotFitALong() {
        givenRequest("v1/loans/99999999999999999999", "loanId", "99999999999999999999");

        underTest.filter(requestContext);

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    @Test
    void ignoresWorkingCapitalLoanRequests() {
        givenRequest("v1/working-capital-loans/42/transactions", "loanId", "42");

        underTest.filter(requestContext);

        assertThat(MDC.get(LoggingContext.LOAN_ID)).isNull();
    }

    private void givenRequest(final String path, final String parameterName, final String parameterValue) {
        final MultivaluedMap<String, String> pathParameters = new MultivaluedHashMap<>();
        pathParameters.putSingle(parameterName, parameterValue);
        given(uriInfo.getPath()).willReturn(path);
        given(uriInfo.getPathParameters()).willReturn(pathParameters);
    }
}
