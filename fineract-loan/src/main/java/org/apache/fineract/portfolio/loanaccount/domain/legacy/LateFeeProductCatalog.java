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
import java.util.Map;
import java.util.Optional;

/**
 * Product control parameters for LNFEECALC. Mirrors LOAD-PRODUCT-TABLE and LNFEECTL.
 */
public final class LateFeeProductCatalog {

    public record Product(String code, int graceDays, int tier1MaxDays, int tier2MaxDays, int tier1RateBp, int tier2RateBp, int tier3RateBp,
            int capPct, BigDecimal minFee) {
    }

    private static final Map<String, Product> PRODUCTS = Map.of( //
            "EVERYDAY", new Product("EVERYDAY", 5, 30, 60, 5, 8, 10, 5, new BigDecimal("1.00")), //
            "CONSUMO", new Product("CONSUMO", 3, 30, 60, 6, 9, 12, 7, new BigDecimal("2.00")), //
            "HIPOTEC", new Product("HIPOTEC", 15, 30, 60, 2, 3, 4, 2, new BigDecimal("5.00")));

    private LateFeeProductCatalog() {}

    public static Optional<Product> find(String productCode) {
        if (productCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRODUCTS.get(productCode.strip()));
    }
}
