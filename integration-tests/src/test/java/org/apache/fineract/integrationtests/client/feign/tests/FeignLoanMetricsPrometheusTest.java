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
package org.apache.fineract.integrationtests.client.feign.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.BusinessStep;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.integrationtests.client.feign.FeignLoanTestBase;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignBusinessStepHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignPrometheusHelper;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignPrometheusHelper.Sample;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignPrometheusHelper.Scrape;
import org.apache.fineract.integrationtests.client.feign.modules.LoanRequestBuilders;
import org.apache.fineract.integrationtests.client.feign.modules.LoanTestData;
import org.apache.fineract.integrationtests.common.Utils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Takes a loan through the steps of <code>fineract seed-loan</code> (client, product, submit, approve, disburse), adds
 * a repayment, a rejected command and an inline Loan COB, and checks what the server then publishes on
 * <code>/actuator/prometheus</code>: the loan command meters, the COB step meters and <code>http_server_requests</code>
 * tagged with the route template. Every count is compared with a scrape taken before the loan was created, so the test
 * gives the same answer on a fresh server and on one that already served other tests.
 */
public class FeignLoanMetricsPrometheusTest extends FeignLoanTestBase {

    private static final String LOAN_COMMANDS = "fineract_loan_commands_total";
    private static final String LOAN_COMMAND_TIMER_COUNT = "fineract_loan_command_execution_seconds_count";
    private static final String LOAN_COMMAND_TIMER_BUCKET = "fineract_loan_command_execution_seconds_bucket";
    private static final String COB_LOANS_PROCESSED = "fineract_cob_loans_processed_total";
    private static final String COB_STEP_TIMER_COUNT = "fineract_cob_step_execution_seconds_count";
    private static final String COB_STEP_TIMER_BUCKET = "fineract_cob_step_execution_seconds_bucket";
    private static final String HTTP_REQUESTS = "http_server_requests_seconds_count";

    private static final List<String> COMMANDS = List.of("submit", "approve", "disburse", "repay");
    private static final List<String> COMMAND_OUTCOMES = List.of("success", "awaiting_approval", "duplicate", "client_error",
            "server_error");

    private static final String LOAN_COB_JOB = "LOAN_CLOSE_OF_BUSINESS";
    private static final String SEED_DATE = "01 January 2026";
    private static final String FIRST_INSTALLMENT_DATE = "01 February 2026";
    private static final double PRINCIPAL = 12000.0;
    private static final int NUMBER_OF_REPAYMENTS = 12;
    private static final Long NO_SUCH_LOAN = Long.MAX_VALUE;
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("(^|/)\\d+(/|$)");

    @Test
    void loanLifecycleAndInlineCobAppearOnThePrometheusEndpointAsBoundedSeries() {
        Scrape before = FeignPrometheusHelper.scrape();

        Long clientId = createClient(SEED_DATE);
        Long productId = createLoanProduct(seedLoanProduct());
        Long loanId = applyForLoan(seedLoanApplication(clientId, productId));
        approveLoan(loanId, approveLoanRequest(PRINCIPAL, SEED_DATE));
        disburseLoan(loanId, LoanRequestBuilders.disburseLoan(PRINCIPAL, SEED_DATE));
        addRepayment(loanId, repayment(1000.0, FIRST_INSTALLMENT_DATE));
        CallFailedRuntimeException rejectedApproval = assertThrows(CallFailedRuntimeException.class,
                () -> approveLoan(loanId, approveLoanRequest(PRINCIPAL, SEED_DATE)), "approving a loan that is already active");
        String rejectedStatus = String.valueOf(rejectedApproval.getStatus());
        assertTrue(rejectedApproval.getStatus() >= 400 && rejectedApproval.getStatus() < 500, "rejected with " + rejectedStatus);
        getLoanDetails(loanId);
        CallFailedRuntimeException missingLoan = assertThrows(CallFailedRuntimeException.class, () -> getLoanDetails(NO_SUCH_LOAN));
        assertEquals(404, missingLoan.getStatus());
        executeInlineCOB(loanId);

        Scrape after = FeignPrometheusHelper.scrape();
        List<String> ids = List.of(String.valueOf(loanId), String.valueOf(clientId), String.valueOf(productId));

        assertAll(//
                () -> assertAll("loan command meters", //
                        () -> assertLoanCommandSeriesAreAllPublished(after), //
                        () -> assertLoanCommandDeltas(before, after), //
                        () -> assertLabelKeys(after, "fineract_loan_command", Set.of("application", "command", "outcome", "le")), //
                        () -> assertNoLabelValueIsAnIdentifier(after, "fineract_loan_command", ids), //
                        () -> assertEquals(bucketBoundaries(0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0),
                                bucketBoundaries(after, LOAN_COMMAND_TIMER_BUCKET), "buckets of fineract_loan_command_execution_seconds")),
                () -> assertAll("COB step meters", //
                        () -> assertCobDeltas(before, after), //
                        () -> assertLabelKeys(after, "fineract_cob_", Set.of("application", "step", "outcome", "le")), //
                        () -> assertNoLabelValueIsAnIdentifier(after, "fineract_cob_", ids), //
                        () -> assertEquals(bucketBoundaries(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 1.0, 5.0),
                                bucketBoundaries(after, COB_STEP_TIMER_BUCKET), "buckets of fineract_cob_step_execution_seconds")),
                // The request observation is closed after the response has been written, so the last requests may not
                // be on a scrape taken straight away
                () -> Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                        .untilAsserted(() -> assertHttpServerRequests(before, FeignPrometheusHelper.scrape(), rejectedStatus, ids)));
    }

    private static void assertLoanCommandSeriesAreAllPublished(Scrape scrape) {
        Set<String> expected = new TreeSet<>();
        for (String command : COMMANDS) {
            for (String outcome : COMMAND_OUTCOMES) {
                expected.add(command + "/" + outcome);
            }
        }
        Set<String> published = scrape.named(LOAN_COMMANDS).stream().map(sample -> sample.label("command") + "/" + sample.label("outcome"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(expected, published, "every command and outcome is a series from startup, and there is no other");
        assertEquals(20, scrape.named(LOAN_COMMANDS).size(), "one series for each command and outcome");
    }

    private static void assertLoanCommandDeltas(Scrape before, Scrape after) {
        Map<String, Double> expected = Map.of("submit/success", 1.0, "approve/success", 1.0, "disburse/success", 1.0, "repay/success", 1.0,
                "approve/client_error", 1.0);
        List<Executable> checks = new ArrayList<>();
        for (String command : COMMANDS) {
            for (String outcome : COMMAND_OUTCOMES) {
                Map<String, String> labels = Map.of("command", command, "outcome", outcome);
                double expectedDelta = expected.getOrDefault(command + "/" + outcome, 0.0);
                checks.add(() -> assertEquals(expectedDelta, delta(before, after, LOAN_COMMANDS, labels), LOAN_COMMANDS + labels));
                checks.add(() -> assertEquals(expectedDelta, delta(before, after, LOAN_COMMAND_TIMER_COUNT, labels),
                        LOAN_COMMAND_TIMER_COUNT + labels));
            }
        }
        assertAll("one sample for each loan command sent, under its command and outcome", checks);
    }

    private void assertCobDeltas(Scrape before, Scrape after) {
        double processed = delta(before, after, COB_LOANS_PROCESSED, Map.of("outcome", "success"));
        List<String> configuredSteps = new FeignBusinessStepHelper(fineractClient()).getConfiguredBusinessStepsByJobName(LOAN_COB_JOB)
                .getBusinessSteps().stream().map(BusinessStep::getStepName).toList();

        assertFalse(configuredSteps.isEmpty(), "Loan COB has business steps configured");
        assertEquals(Set.of("failure", "success"), after.labelValues(COB_LOANS_PROCESSED, "outcome"),
                "both outcomes are series from startup");
        assertTrue(processed >= 1.0, "the inline COB counted the loan as processed, delta " + processed);
        assertEquals(0.0, delta(before, after, COB_LOANS_PROCESSED, Map.of("outcome", "failure")), "no loan failed");
        assertEquals(0.0, delta(before, after, COB_STEP_TIMER_COUNT, Map.of("outcome", "failure")), "no step failed");
        for (String step : configuredSteps) {
            assertEquals(processed, delta(before, after, COB_STEP_TIMER_COUNT, Map.of("step", step, "outcome", "success")),
                    "step " + step + " ran once for each loan processed");
        }
        assertEquals(processed * configuredSteps.size(), delta(before, after, COB_STEP_TIMER_COUNT, Map.of()),
                "only the configured steps were timed");
    }

    /**
     * Looks at <code>http_server_requests_seconds</code> only. Its sibling
     * <code>http_server_requests_active_seconds</code>, the timer of requests still in flight, is tagged when a request
     * starts, before any route is matched, so it always reads <code>uri="UNKNOWN"</code>, with one series for each HTTP
     * method.
     */
    private static void assertHttpServerRequests(Scrape before, Scrape after, String rejectedStatus, List<String> ids) {
        assertAll("http_server_requests", //
                () -> assertRequests(before, after, 1, "POST", "/api/v1/clients", "200"), //
                () -> assertRequests(before, after, 1, "POST", "/api/v1/loanproducts", "200"), //
                () -> assertRequests(before, after, 1, "POST", "/api/v1/loans", "200"), //
                () -> assertRequests(before, after, 2, "POST", "/api/v1/loans/{loanId}", "200"), //
                () -> assertRequests(before, after, 1, "POST", "/api/v1/loans/{loanId}", rejectedStatus), //
                () -> assertRequests(before, after, 1, "POST", "/api/v1/loans/{loanId}/transactions", "200"), //
                () -> assertRequests(before, after, 1, "GET", "/api/v1/loans/{loanId}", "200"), //
                () -> assertRequests(before, after, 1, "GET", "/api/v1/loans/{loanId}", "404"), //
                () -> assertRequests(before, after, 1, "POST", "/api/v1/jobs/{jobName}/inline", "200"), //
                () -> assertEquals(0.0, delta(before, after, HTTP_REQUESTS, Map.of("uri", "UNKNOWN")),
                        "every request of this test matched a route, so none may be counted under uri=\"UNKNOWN\""), //
                () -> assertEquals(0.0, delta(before, after, HTTP_REQUESTS, Map.of("uri", "NOT_FOUND")),
                        "a loan that does not exist is still a matched route, not uri=\"NOT_FOUND\""), //
                () -> assertEquals(List.of(),
                        after.labelValues(HTTP_REQUESTS, "uri").stream()
                                .filter(uri -> uri.startsWith("/api/") && NUMERIC_PATH_SEGMENT.matcher(uri).find()).sorted().toList(),
                        "no uri of the API carries an identifier"), //
                () -> assertLabelKeys(after, "http_server_requests_seconds",
                        Set.of("application", "method", "uri", "status", "outcome", "exception", "error", "le")), //
                () -> assertEquals(List.of(),
                        after.matching(sample -> sample.name().equals(HTTP_REQUESTS) && sample.label("uri").startsWith("/api/v1/loans")
                                && ids.stream().anyMatch(id -> List.of(sample.label("uri").split("/", -1)).contains(id))),
                        "the loan, client and product ids of this test are in no uri"));
    }

    private static void assertRequests(Scrape before, Scrape after, int atLeast, String method, String uri, String status) {
        Map<String, String> labels = Map.of("method", method, "uri", uri, "status", status);
        double requests = delta(before, after, HTTP_REQUESTS, labels);
        assertTrue(requests >= atLeast, HTTP_REQUESTS + labels + " grew by " + requests + ", expected at least " + atLeast);
    }

    private static void assertLabelKeys(Scrape scrape, String namePrefix, Set<String> allowed) {
        List<Sample> samples = scrape.matching(sample -> sample.name().startsWith(namePrefix));

        assertFalse(samples.isEmpty(), "no series starts with " + namePrefix);
        assertEquals(List.of(), samples.stream().filter(sample -> !allowed.containsAll(sample.labels().keySet())).toList(),
                "series of " + namePrefix + "* with a label other than " + new TreeSet<>(allowed));
    }

    private static void assertNoLabelValueIsAnIdentifier(Scrape scrape, String namePrefix, List<String> ids) {
        assertEquals(List.of(),
                scrape.matching(sample -> sample.name().startsWith(namePrefix) && sample.labels().entrySet().stream()
                        .anyMatch(label -> !label.getKey().equals("le") && ids.contains(label.getValue()))),
                "series of " + namePrefix + "* with a loan, client or product id as a label value");
    }

    private static double delta(Scrape before, Scrape after, String name, Map<String, String> labels) {
        return after.sum(name, labels) - before.sum(name, labels);
    }

    private static Set<Double> bucketBoundaries(double... finiteBoundaries) {
        Set<Double> boundaries = new TreeSet<>();
        for (double boundary : finiteBoundaries) {
            boundaries.add(boundary);
        }
        boundaries.add(Double.POSITIVE_INFINITY);
        return boundaries;
    }

    private static Set<Double> bucketBoundaries(Scrape scrape, String bucketName) {
        return scrape.labelValues(bucketName, "le").stream().map(le -> "+Inf".equals(le) ? Double.POSITIVE_INFINITY : Double.valueOf(le))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The product of <code>fineract seed-loan</code>: 12 monthly repayments, 1% flat interest a month, no accounting.
     */
    private static PostLoanProductsRequest seedLoanProduct() {
        return new PostLoanProductsRequest()//
                .name(Utils.uniqueRandomStringGenerator("METRICS_", 6))//
                .shortName(Utils.uniqueRandomStringGenerator("", 4))//
                .currencyCode("USD")//
                .digitsAfterDecimal(2)//
                .inMultiplesOf(0)//
                .principal(PRINCIPAL)//
                .numberOfRepayments(NUMBER_OF_REPAYMENTS)//
                .repaymentEvery(1)//
                .repaymentFrequencyType(LoanTestData.RepaymentFrequencyType.MONTHS_L)//
                .interestRatePerPeriod(1.0)//
                .interestRateFrequencyType(LoanTestData.InterestRateFrequencyType.MONTHS)//
                .amortizationType(LoanTestData.AmortizationType.EQUAL_INSTALLMENTS)//
                .interestType(LoanTestData.InterestType.FLAT)//
                .interestCalculationPeriodType(LoanTestData.InterestCalculationPeriodType.SAME_AS_REPAYMENT_PERIOD)//
                .transactionProcessingStrategyCode(LoanTestData.TransactionProcessingStrategyCode.MIFOS_STANDARD_STRATEGY)//
                .accountingRule(1)//
                .daysInYearType(LoanTestData.DaysInYearType.ACTUAL)//
                .daysInMonthType(LoanTestData.DaysInMonthType.ACTUAL)//
                .isInterestRecalculationEnabled(false)//
                .locale(LoanTestData.LOCALE);
    }

    private static PostLoansRequest seedLoanApplication(Long clientId, Long productId) {
        return LoanRequestBuilders.applyLoan(clientId, productId, SEED_DATE, PRINCIPAL, NUMBER_OF_REPAYMENTS)//
                .interestRatePerPeriod(BigDecimal.ONE)//
                .interestType(LoanTestData.InterestType.FLAT);
    }
}
