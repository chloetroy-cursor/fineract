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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.legacy.LateFeeProductCatalog.Product;

/**
 * Pure-Java port of legacy/cobol/programs/LNFEECALC.cbl. Rules are locked by legacy/cobol/SPEC.md and
 * legacy/cobol/fixtures/cases.jsonl.
 */
public final class LateFeeCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int INTERMEDIATE_SCALE = 6;
    private static final RoundingMode HALF_UP = RoundingMode.HALF_UP;

    private static final String STATUS_OK = "00";
    private static final String STATUS_GRACE = "01";
    private static final String STATUS_ZERO_PRIN = "02";
    private static final String STATUS_BAD_PROD = "99";

    private static final String MSG_OK = "FEE ASSESSED";
    private static final String MSG_GRACE = "WITHIN GRACE PERIOD";
    private static final String MSG_ZERO_PRIN = "ZERO PRINCIPAL - NO FEE";
    private static final String MSG_BAD_PROD = "UNKNOWN PRODUCT CODE";

    private LateFeeCalculator() {}

    public static LateFeeAssessment assess(BigDecimal principalOutstanding, int daysOverdue, String productCode) {
        Optional<Product> productOpt = LateFeeProductCatalog.find(productCode);
        if (productOpt.isEmpty()) {
            return new LateFeeAssessment(ZERO, 0, STATUS_BAD_PROD, MSG_BAD_PROD);
        }
        Product product = productOpt.get();

        if (principalOutstanding == null || principalOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            return new LateFeeAssessment(ZERO, 0, STATUS_ZERO_PRIN, MSG_ZERO_PRIN);
        }

        if (daysOverdue <= product.graceDays()) {
            return new LateFeeAssessment(ZERO, 0, STATUS_GRACE, MSG_GRACE);
        }

        int billableDays = daysOverdue - product.graceDays();
        int remaining = billableDays;
        int tier1Days;
        int tier2Days = 0;
        int tier3Days = 0;

        if (remaining > product.tier1MaxDays()) {
            tier1Days = product.tier1MaxDays();
            remaining -= product.tier1MaxDays();
        } else {
            tier1Days = remaining;
            remaining = 0;
        }

        int tier2Span = product.tier2MaxDays() - product.tier1MaxDays();
        if (remaining > 0) {
            if (remaining > tier2Span) {
                tier2Days = tier2Span;
                remaining -= tier2Span;
            } else {
                tier2Days = remaining;
                remaining = 0;
            }
        }
        if (remaining > 0) {
            tier3Days = remaining;
        }

        BigDecimal tier1Fee = tierFee(principalOutstanding, product.tier1RateBp(), tier1Days);
        BigDecimal tier2Fee = tierFee(principalOutstanding, product.tier2RateBp(), tier2Days);
        BigDecimal tier3Fee = tierFee(principalOutstanding, product.tier3RateBp(), tier3Days);
        BigDecimal rawFee = tier1Fee.add(tier2Fee).add(tier3Fee);

        BigDecimal capAmt = principalOutstanding.multiply(BigDecimal.valueOf(product.capPct())).divide(ONE_HUNDRED, INTERMEDIATE_SCALE,
                HALF_UP);
        BigDecimal capped = rawFee.min(capAmt);
        BigDecimal rounded = capped.setScale(2, HALF_UP);

        if (rounded.compareTo(BigDecimal.ZERO) > 0 && rounded.compareTo(product.minFee()) < 0) {
            rounded = product.minFee();
        }

        return new LateFeeAssessment(rounded, billableDays, STATUS_OK, MSG_OK);
    }

    private static BigDecimal tierFee(BigDecimal principal, int rateBp, int days) {
        if (days == 0) {
            return BigDecimal.ZERO;
        }
        return principal.multiply(BigDecimal.valueOf(rateBp)).multiply(BigDecimal.valueOf(days)).divide(TEN_THOUSAND, INTERMEDIATE_SCALE,
                HALF_UP);
    }
}
