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
package org.apache.fineract.cob.loan;

import org.apache.fineract.cob.COBBusinessStepService;
import org.apache.fineract.cob.processor.AbstractItemProcessor;
import org.apache.fineract.infrastructure.core.logging.LoggingContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.service.ProgressiveLoanModelProcessingService;
import org.apache.fineract.portfolio.loanproduct.calc.data.ProgressiveLoanInterestScheduleModel;
import org.jspecify.annotations.NonNull;

public abstract class AbstractLoanItemProcessor extends AbstractItemProcessor<Loan> {

    private final ProgressiveLoanModelProcessingService progressiveLoanModelProcessingService;

    public AbstractLoanItemProcessor(COBBusinessStepService cobBusinessStepService,
            ProgressiveLoanModelProcessingService progressiveLoanModelProcessingService) {
        super(cobBusinessStepService);
        this.progressiveLoanModelProcessingService = progressiveLoanModelProcessingService;
    }

    @Override
    public Loan process(@NonNull Loan loan) throws Exception {
        // COB runs on batch worker threads with no request around it, so the loan id is put on the log lines here.
        final LoggingContext.Scope loanScope = LoggingContext.withLoanId(loan.getId());
        try {
            if (needToRebuildModel(loan)) {
                progressiveLoanModelProcessingService.recalculateModelAndSave(loan);
            }
            return super.process(loan);
        } finally {
            loanScope.close();
        }
    }

    private boolean needToRebuildModel(Loan loan) {
        return loan.isProgressiveSchedule() && !progressiveLoanModelProcessingService.hasValidModel(loan.getId(),
                ProgressiveLoanInterestScheduleModel.getModelVersion());
    }

    @Override
    public void setLastRun(Loan processedLoan) {
        processedLoan.setLastClosedBusinessDate(getBusinessDate());
    }

}
