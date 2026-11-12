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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.jackson.JacksonJsonFormatter;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class FineractJsonLayoutTest {

    private static final String LOGGER_NAME = "org.apache.fineract.test.JsonLayout";

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private Logger logger;
    private OutputStreamAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachJsonAppender() {
        MDC.clear();
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // the layout is set up the way logback-spring.xml sets it up
        final FineractJsonLayout layout = new FineractJsonLayout();
        layout.setContext(context);
        layout.setJsonFormatter(new JacksonJsonFormatter());
        layout.setTimestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        layout.setTimestampFormatTimezoneId("UTC");
        layout.setAppendLineSeparator(true);
        layout.start();

        final LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.setLayout(layout);
        encoder.start();

        appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(output);
        appender.start();

        logger = context.getLogger(LOGGER_NAME);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);
    }

    @AfterEach
    void detachJsonAppender() {
        logger.detachAppender(appender);
        logger.setAdditive(true);
        logger.setLevel(null);
        appender.stop();
        MDC.clear();
    }

    @Test
    void tenantCorrelationIdAndLoanIdAreFieldsOfTheirOwn() throws Exception {
        MDC.put(LoggingContext.TENANT_ID, "default");
        MDC.put(LoggingContext.CORRELATION_ID, "abc-123");
        MDC.put(LoggingContext.LOAN_ID, "42");

        logger.info("Repayment of {} received", 100);

        final JsonNode line = singleLine();
        assertThat(line.get("tenantId").asText()).isEqualTo("default");
        assertThat(line.get("correlationId").asText()).isEqualTo("abc-123");
        assertThat(line.get("loanId").asText()).isEqualTo("42");
        assertThat(line.get("level").asText()).isEqualTo("INFO");
        assertThat(line.get("logger").asText()).isEqualTo(LOGGER_NAME);
        assertThat(line.get("thread").asText()).isEqualTo(Thread.currentThread().getName());
        assertThat(line.get("message").asText()).isEqualTo("Repayment of 100 received");
        assertThat(line.get("timestamp").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        // the identifiers are not repeated in a nested mdc object
        assertThat(line.has("mdc")).isFalse();
        assertThat(line.has("exception")).isFalse();
    }

    @Test
    void identifiersThatAreNotKnownAreLeftOut() throws Exception {
        MDC.put(LoggingContext.TENANT_ID, "default");

        logger.info("Scheduler started");

        final JsonNode line = singleLine();
        assertThat(line.get("tenantId").asText()).isEqualTo("default");
        assertThat(line.has("correlationId")).isFalse();
        assertThat(line.has("loanId")).isFalse();
    }

    @Test
    void otherMdcEntriesStayUnderMdc() throws Exception {
        MDC.put(LoggingContext.LOAN_ID, "42");
        MDC.put("jobName", "LOAN_COB");

        logger.info("Step finished");

        final JsonNode line = singleLine();
        assertThat(line.get("loanId").asText()).isEqualTo("42");
        assertThat(line.get("mdc").size()).isEqualTo(1);
        assertThat(line.get("mdc").get("jobName").asText()).isEqualTo("LOAN_COB");
    }

    @Test
    void theStackTraceIsInTheExceptionField() throws Exception {
        logger.error("Loan COB failed", new IllegalStateException("No business steps found"));

        final JsonNode line = singleLine();
        assertThat(line.get("level").asText()).isEqualTo("ERROR");
        assertThat(line.get("exception").asText()).contains("java.lang.IllegalStateException: No business steps found")
                .contains("FineractJsonLayoutTest");
    }

    @Test
    void everyEventIsOneLineHoldingOneJsonObject() throws Exception {
        logger.info("first\nmessage with a line break");
        logger.warn("second", new IllegalStateException("with a stack trace"));

        final List<JsonNode> lines = lines();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("message").asText()).isEqualTo("first\nmessage with a line break");
        assertThat(lines.get(1).get("message").asText()).isEqualTo("second");
    }

    private JsonNode singleLine() throws Exception {
        final List<JsonNode> lines = lines();
        assertThat(lines).hasSize(1);
        return lines.get(0);
    }

    private List<JsonNode> lines() throws Exception {
        final String written = output.toString(StandardCharsets.UTF_8);
        assertThat(written).endsWith(System.lineSeparator());
        final ObjectMapper mapper = new ObjectMapper();
        final List<JsonNode> lines = new ArrayList<>();
        for (String line : written.lines().toList()) {
            final JsonNode node = mapper.readTree(line);
            assertThat(node.isObject()).isTrue();
            lines.add(node);
        }
        return lines;
    }
}
