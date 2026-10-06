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
package org.apache.fineract.integrationtests.client.feign.modules;

import org.apache.fineract.client.models.PostProductsTypeRequest;
import org.apache.fineract.integrationtests.common.Utils;

public final class ShareProductRequestBuilders {

    private static final String CURRENCY = "USD";
    private static final String LOCALE = "en_GB";
    private static final int DAYS = 0;

    private ShareProductRequestBuilders() {}

    /**
     * The share product the share integration tests have always used: 10,000 shares at 2.00 each, no accounting, a
     * one-day lock-in and a one-day minimum active period for dividends.
     */
    public static PostProductsTypeRequest defaultShareProduct() {
        return new PostProductsTypeRequest()//
                .name(Utils.uniqueRandomStringGenerator("SHARE_PRODUCT_", 6))//
                .shortName(Utils.uniqueRandomStringGenerator("", 4))//
                .description(Utils.randomStringGenerator("", 20))//
                .currencyCode(CURRENCY)//
                .locale(LOCALE)//
                .digitsAfterDecimal(4)//
                .inMultiplesOf(0)//
                .totalShares(10000)//
                .sharesIssued(10000)//
                .unitPrice(2)//
                .minimumShares(10)//
                .nominalShares(20)//
                .maximumShares(3000)//
                .allowDividendCalculationForInactiveClients(true)//
                .accountingRule(SavingsTestData.AccountingRule.NONE)//
                .minimumActivePeriodForDividends(1)//
                .minimumactiveperiodFrequencyType(DAYS)//
                .lockinPeriodFrequency(1)//
                .lockinPeriodFrequencyType(DAYS);
    }
}
