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
package org.apache.fineract.portfolio.loanaccount.domain.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Characterization suite for {@link LateFeeCalculator}. Expected values come from legacy/cobol/fixtures/cases.jsonl
 * (canonical oracle for LNFEECALC).
 */
class LateFeeCalculatorTest {

    private static final Gson GSON = new Gson();
    private static final String FIXTURE_RELATIVE = "legacy/cobol/fixtures/cases.jsonl";

    static Stream<Arguments> cases() throws IOException {
        Path fixtures = resolveFixtures();
        return Files.lines(fixtures).filter(line -> !line.isBlank()).map(line -> {
            JsonObject root = GSON.fromJson(line, JsonObject.class);
            String id = root.get("id").getAsString();
            JsonObject input = root.getAsJsonObject("input");
            JsonObject expected = root.getAsJsonObject("expected");
            return Arguments.of(id, input.get("principalOutstanding").getAsString(), input.get("daysOverdue").getAsInt(),
                    input.get("productCode").getAsString(), expected.get("lateFeeAmt").getAsString(),
                    expected.get("billableDays").getAsInt(), expected.get("statusCode").getAsString(),
                    expected.get("statusMsg").getAsString());
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matchesOracle(String id, String principalOutstanding, int daysOverdue, String productCode, String expectedLateFeeAmt,
            int expectedBillableDays, String expectedStatusCode, String expectedStatusMsg) {
        LateFeeAssessment result = LateFeeCalculator.assess(new BigDecimal(principalOutstanding), daysOverdue, productCode);

        assertNotNull(result, id);
        assertEquals(new BigDecimal(expectedLateFeeAmt), result.lateFeeAmt(), id + " lateFeeAmt");
        assertEquals(expectedBillableDays, result.billableDays(), id + " billableDays");
        assertEquals(expectedStatusCode, result.statusCode(), id + " statusCode");
        assertEquals(expectedStatusMsg, result.statusMsg(), id + " statusMsg");
    }

    private static Path resolveFixtures() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(FIXTURE_RELATIVE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find " + FIXTURE_RELATIVE + " from " + Path.of("").toAbsolutePath());
    }
}
