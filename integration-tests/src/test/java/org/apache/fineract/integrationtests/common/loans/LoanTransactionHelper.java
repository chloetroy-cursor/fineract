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
package org.apache.fineract.integrationtests.common.loans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.BuyDownFeeAmortizationDetails;
import org.apache.fineract.client.models.CapitalizedIncomeDetails;
import org.apache.fineract.client.models.CommandProcessingResult;
import org.apache.fineract.client.models.DeleteLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.client.models.DeleteLoansLoanIdResponse;
import org.apache.fineract.client.models.DisbursementDetail;
import org.apache.fineract.client.models.GetDelinquencyActionsResponse;
import org.apache.fineract.client.models.GetDelinquencyTagHistoryResponse;
import org.apache.fineract.client.models.GetLoansApprovalTemplateResponse;
import org.apache.fineract.client.models.GetLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdChargesTemplateResponse;
import org.apache.fineract.client.models.GetLoansLoanIdDelinquencySummary;
import org.apache.fineract.client.models.GetLoansLoanIdDisbursementDetails;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentSchedule;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdSummary;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.GetLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactionsTemplateResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.client.models.GetLoansResponse;
import org.apache.fineract.client.models.InterestPauseRequestDto;
import org.apache.fineract.client.models.PostAddAndDeleteDisbursementDetailRequest;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansDelinquencyActionRequest;
import org.apache.fineract.client.models.PostLoansDelinquencyActionResponse;
import org.apache.fineract.client.models.PostLoansLoanIdChargesChargeIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.client.models.PostLoansLoanIdChargesRequest;
import org.apache.fineract.client.models.PostLoansLoanIdChargesResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PutChargeTransactionChangesRequest;
import org.apache.fineract.client.models.PutChargeTransactionChangesResponse;
import org.apache.fineract.client.models.PutLoanProductsProductIdRequest;
import org.apache.fineract.client.models.PutLoanProductsProductIdResponse;
import org.apache.fineract.client.models.PutLoansLoanIdChargesChargeIdRequest;
import org.apache.fineract.client.models.PutLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.client.models.PutLoansLoanIdRequest;
import org.apache.fineract.client.models.PutLoansLoanIdResponse;
import org.apache.fineract.client.models.TransactionType;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.portfolio.delinquency.domain.DelinquencyAction;
import retrofit2.Response;

@Slf4j
@SuppressWarnings({ "rawtypes", "unchecked" })
public class LoanTransactionHelper {

    public static final String DATE_FORMAT = "d MMMM yyyy";

    public static LocalDate getMaxTransactionDateOfActiveLoans() {
        return Calls.ok(FineractClientHelper.getFineractClient().legacy.getMaxTransactionDateOfActiveLoans());
    }

    public PutLoansLoanIdResponse modifyLoanApplication(final String loanExternalId, final String command,
            final PutLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.updateLoanApplicationByExternalId(loanExternalId, request, command));
    }

    public List<GetDelinquencyActionsResponse> getLoanDelinquencyActions(final Long loanID) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.retrieveDelinquencyActionsLoan(loanID));
    }

    public List<GetDelinquencyActionsResponse> getLoanDelinquencyActions(String externalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.retrieveDelinquencyActionsLoanByExternalId(externalId));
    }

    public PostLoansDelinquencyActionResponse createLoanDelinquencyAction(final Long loanid, DelinquencyAction action, String startDate,
            String endDate) {
        PostLoansDelinquencyActionRequest postLoansDelinquencyAction = new PostLoansDelinquencyActionRequest().action(action.name())
                .startDate(startDate).endDate(endDate).locale("en").dateFormat("dd MMMM yyyy");
        return Calls.ok(FineractClientHelper.getFineractClient().loans.createDelinquencyActionLoan(loanid, postLoansDelinquencyAction));
    }

    public PostLoansDelinquencyActionResponse createLoanDelinquencyAction(String externalId, DelinquencyAction action, String startDate,
            String endDate) {
        PostLoansDelinquencyActionRequest postLoansDelinquencyAction = new PostLoansDelinquencyActionRequest().action(action.name())
                .startDate(startDate).endDate(endDate).locale("en").dateFormat("dd MMMM yyyy");
        return Calls.ok(FineractClientHelper.getFineractClient().loans.createDelinquencyActionLoanByExternalId(externalId,
                postLoansDelinquencyAction));
    }

    public PostLoansDelinquencyActionResponse createLoanDelinquencyAction(final Long loanid, DelinquencyAction action, String startDate) {
        return createLoanDelinquencyAction(loanid, action, startDate, null);
    }

    public List<GetLoansLoanIdChargesChargeIdResponse> getLoanCharges(final Long loanId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.retrieveAllLoanCharges(loanId));
    }

    public List<GetLoansLoanIdChargesChargeIdResponse> getLoanCharges(final String loanExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.retrieveAllLoanChargesByLoanExternalId(loanExternalId));
    }

    public GetLoansLoanIdChargesTemplateResponse getLoanChargeTemplate(final Long loanId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.retrieveTemplateLoanCharge(loanId));
    }

    public GetLoansLoanIdChargesTemplateResponse getLoanChargeTemplate(final String loanExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.retrieveTemplateLoanChargeByLoanExternalId(loanExternalId));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanRepayment(final Long loanId, final String command, final String date,
            final Double amountToBePaid) {
        log.info("Make loan transaction. Command - {} with amount {} in {} for Loan {}", command, amountToBePaid, date, loanId);
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId,
                new PostLoansLoanIdTransactionsRequest().transactionAmount(amountToBePaid).transactionDate(date).dateFormat("dd MMMM yyyy")
                        .locale("en"),
                command));
    }

    public PostLoansLoanIdTransactionsResponse executeLoanTransaction(final Long loanId, final PostLoansLoanIdTransactionsRequest request,
            final String command) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, command));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanRepayment(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "repayment"));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanRepayment(final Long loanId, final PostLoansLoanIdTransactionsRequest request,
            final String user, final String pass) {
        return Calls.ok(FineractClientHelper.createNewFineractClient(user, pass).loanTransactions.handleCommandsLoanTransaction(loanId,
                request, "repayment"));
    }

    public PostLoansLoanIdTransactionsResponse addCapitalizedIncome(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "capitalizedIncome"));
    }

    public PostLoansLoanIdTransactionsResponse addCapitalizedIncome(final Long loanId, final String transactionDate, final double amount) {
        return addCapitalizedIncome(loanId, new PostLoansLoanIdTransactionsRequest().transactionAmount(amount)
                .transactionDate(transactionDate).dateFormat("dd MMMM yyyy").locale("en"));
    }

    public PostLoansLoanIdTransactionsResponse addCapitalizedIncome(final Long loanId, final String transactionDate, final double amount,
            final Long classificationId) {
        return addCapitalizedIncome(loanId, new PostLoansLoanIdTransactionsRequest().transactionAmount(amount)
                .transactionDate(transactionDate).dateFormat("dd MMMM yyyy").locale("en").classificationId(classificationId));
    }

    public Response<CommandProcessingResult> createInterestPause(Long loanId, String startDate, String endDate) {
        log.info("Creating interest pause for Loan {} from {} to {}", loanId, startDate, endDate);
        return Calls.executeU(FineractClientHelper.getFineractClient().loanInterestPauseApi.createLoanInterestPause(loanId,
                new InterestPauseRequestDto().startDate(startDate).endDate(endDate).dateFormat(DATE_FORMAT).locale("en")));
    }

    public PostLoansLoanIdTransactionsResponse capitalizedIncomeAdjustment(final Long loanId, final Long capitalizedIncomeTransactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId,
                capitalizedIncomeTransactionId, request, "capitalizedIncomeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse capitalizedIncomeAdjustment(final String loanExternalId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanExternalId(loanExternalId,
                transactionId, request, "capitalizedIncomeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse capitalizedIncomeAdjustment(final String loanExternalId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanAndTransactionExternalId(
                loanExternalId, transactionExternalId, request, "capitalizedIncomeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse capitalizedIncomeAdjustment(final Long loanId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByTransactionExternalId(loanId,
                transactionExternalId, request, "capitalizedIncomeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse capitalizedIncomeAdjustment(final Long loanId, final Long capitalizedIncomeTransactionId,
            final String transactionDate, final double amount) {
        return capitalizedIncomeAdjustment(loanId, capitalizedIncomeTransactionId, new PostLoansLoanIdTransactionsTransactionIdRequest()
                .transactionAmount(amount).transactionDate(transactionDate).dateFormat("dd MMMM yyyy").locale("en"));
    }

    public PostLoansLoanIdTransactionsResponse buyDownFeeAdjustment(final Long loanId, final Long buyDownFeeTransactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId, buyDownFeeTransactionId,
                request, "buyDownFeeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse buyDownFeeAdjustment(final String loanExternalId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanExternalId(loanExternalId,
                transactionId, request, "buyDownFeeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse buyDownFeeAdjustment(final String loanExternalId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanAndTransactionExternalId(
                loanExternalId, transactionExternalId, request, "buyDownFeeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse buyDownFeeAdjustment(final Long loanId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByTransactionExternalId(loanId,
                transactionExternalId, request, "buyDownFeeAdjustment"));
    }

    public PostLoansLoanIdTransactionsResponse buyDownFeeAdjustment(final Long loanId, final Long buyDownFeeTransactionId,
            final String transactionDate, final double amount) {
        return buyDownFeeAdjustment(loanId, buyDownFeeTransactionId, new PostLoansLoanIdTransactionsTransactionIdRequest()
                .transactionAmount(amount).transactionDate(transactionDate).dateFormat("dd MMMM yyyy").locale("en"));
    }

    public PostLoansLoanIdTransactionsResponse makeInterestPaymentWaiver(final Long loanId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "interestPaymentWaiver"));
    }

    public PostLoansLoanIdTransactionsResponse makeInterestPaymentWaiver(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "interestPaymentWaiver"));
    }

    public PostLoansLoanIdTransactionsResponse reAge(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "reAge"));
    }

    public PostLoansLoanIdTransactionsResponse reAmortize(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "reAmortize"));
    }

    public PostLoansLoanIdTransactionsResponse undoReAge(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "undoReAge"));
    }

    public PostLoansLoanIdTransactionsResponse undoReAmortize(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "undoReAmortize"));
    }

    public PutChargeTransactionChangesResponse undoWaiveLoanCharge(final Long loanId, final Long transactionId,
            final PutChargeTransactionChangesRequest request) {
        log.info("--------------------------------- UNDO WAIVE CHARGES FOR LOAN --------------------------------");
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.undoWaiveChargeLoanTransaction(loanId, transactionId, request));
    }

    public PutChargeTransactionChangesResponse undoWaiveLoanCharge(final Long loanId, final String transactionExternalId,
            final PutChargeTransactionChangesRequest request) {
        log.info("--------------------------------- UNDO WAIVE CHARGES FOR LOAN --------------------------------");
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .undoWaiveChargeLoanTransactionByTransactionExternalId(loanId, transactionExternalId, request));
    }

    public PutChargeTransactionChangesResponse undoWaiveLoanCharge(final String loanExternalId, final Long transactionId,
            final PutChargeTransactionChangesRequest request) {
        log.info("--------------------------------- UNDO WAIVE CHARGES FOR LOAN --------------------------------");
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .undoWaiveChargeLoanTransactionByLoanExternalId(loanExternalId, transactionId, request));
    }

    public PutChargeTransactionChangesResponse undoWaiveLoanCharge(final String loanExternalId, final String transactionExternalId,
            final PutChargeTransactionChangesRequest request) {
        log.info("--------------------------------- UNDO WAIVE CHARGES FOR LOAN --------------------------------");
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .undoWaiveChargeLoanTransactionByLoanAndTransactionExternalId(loanExternalId, transactionExternalId, request));
    }

    public PostLoansLoanIdChargesChargeIdResponse waiveLoanCharge(final Long loanId, final Long loanChargeId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeOnExistingCharge(loanId, loanChargeId,
                request, "waive"));
    }

    public PostLoansLoanIdChargesChargeIdResponse waiveLoanCharge(final String loanExternalId, final Long loanChargeId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges
                .executeLoanChargeByLoanExternalIdOnExistingCharge(loanExternalId, loanChargeId, request, "waive"));
    }

    public PostLoansLoanIdChargesChargeIdResponse waiveLoanCharge(final Long loanId, final String loanChargeExternalId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeByChargeExternalId(loanId,
                loanChargeExternalId, request, "waive"));
    }

    public PostLoansLoanIdChargesChargeIdResponse waiveLoanCharge(final String loanExternalId, final String loanChargeExternalId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeByLoanAndChargeExternalId(loanExternalId,
                loanChargeExternalId, request, "waive"));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanRepayment(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "repayment"));
    }

    public PostLoansLoanIdTransactionsResponse makeMerchantIssuedRefund(final Long loanId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "merchantIssuedRefund"));
    }

    public PostLoansLoanIdTransactionsResponse makeMerchantIssuedRefund(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "merchantIssuedRefund"));
    }

    public PostLoansLoanIdTransactionsResponse makePayoutRefund(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "payoutRefund"));
    }

    public PostLoansLoanIdTransactionsResponse makePayoutRefund(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "payoutRefund"));
    }

    public PostLoansLoanIdTransactionsResponse makeChargeRefund(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "chargeRefund"));
    }

    public PostLoansLoanIdTransactionsResponse makeChargeRefund(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "chargeRefund"));
    }

    public PostLoansLoanIdTransactionsResponse manualInterestRefund(final Long loanId, final Long targetTransactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId, targetTransactionId,
                request, "interest-refund"));
    }

    public PostLoansLoanIdTransactionsResponse makeGoodwillCredit(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "goodwillCredit"));
    }

    public PostLoansLoanIdTransactionsResponse makeGoodwillCredit(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "goodwillCredit"));
    }

    public PostLoansLoanIdTransactionsResponse makeWaiveInterest(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "waiveinterest"));
    }

    public PostLoansLoanIdTransactionsResponse makeWaiveInterest(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "waiveinterest"));
    }

    public PostLoansLoanIdTransactionsResponse makeWriteoff(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "writeoff"));
    }

    public PostLoansLoanIdTransactionsResponse makeWriteoff(final String loanExternalId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "writeoff"));
    }

    public PostLoansLoanIdTransactionsResponse makeUndoWriteoff(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "undowriteoff"));
    }

    public PostLoansLoanIdTransactionsResponse makeUndoWriteoff(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "undowriteoff"));
    }

    public PostLoansLoanIdTransactionsResponse makeRecoveryPayment(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "recoverypayment"));
    }

    public PostLoansLoanIdTransactionsResponse makeRecoveryPayment(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "recoverypayment"));
    }

    public PostLoansLoanIdTransactionsResponse makeRefundByCash(final Long loanId, final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "refundByCash"));
    }

    public PostLoansLoanIdTransactionsResponse makeRefundByCash(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "refundByCash"));
    }

    public PostLoansLoanIdTransactionsResponse makeCreditBalanceRefund(final Long loanId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "creditBalanceRefund"));
    }

    public PostLoansLoanIdTransactionsResponse makeCreditBalanceRefund(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "creditBalanceRefund"));
    }

    public PostLoansLoanIdTransactionsResponse reverseLoanTransaction(final String loanExternalId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanExternalId(loanExternalId,
                transactionId, request, "undo"));
    }

    public PostLoansLoanIdTransactionsResponse reverseLoanTransaction(final String loanExternalId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .adjustLoanTransactionByLoanAndTransactionExternalId(loanExternalId, transactionExternalId, request, "undo"));
    }

    public PostLoansLoanIdTransactionsResponse reverseLoanTransaction(final Long loanId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByTransactionExternalId(loanId,
                transactionExternalId, request, "undo"));
    }

    public PostLoansLoanIdTransactionsResponse chargebackLoanTransaction(final Long loanId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId, transactionId, request,
                "chargeback"));
    }

    public PostLoansLoanIdTransactionsResponse chargebackLoanTransaction(final String loanExternalId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanExternalId(loanExternalId,
                transactionId, request, "chargeback"));
    }

    public PostLoansLoanIdTransactionsResponse chargebackLoanTransaction(final String loanExternalId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .adjustLoanTransactionByLoanAndTransactionExternalId(loanExternalId, transactionExternalId, request, "chargeback"));
    }

    public PostLoansLoanIdTransactionsResponse chargebackLoanTransaction(final Long loanId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByTransactionExternalId(loanId,
                transactionExternalId, request, "chargeback"));
    }

    public PostLoansLoanIdTransactionsResponse adjustLoanTransaction(final String loanExternalId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByLoanExternalId(loanExternalId,
                transactionId, request, "adjust"));
    }

    public PostLoansLoanIdTransactionsResponse adjustLoanTransaction(final String loanExternalId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .adjustLoanTransactionByLoanAndTransactionExternalId(loanExternalId, transactionExternalId, request, "adjust"));
    }

    public PostLoansLoanIdTransactionsResponse adjustLoanTransaction(final Long loanId, final String transactionExternalId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransactionByTransactionExternalId(loanId,
                transactionExternalId, request, "adjust"));
    }

    public PostLoansLoanIdTransactionsResponse adjustLoanTransaction(final Long loanId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId, transactionId, request, "adjust"));
    }

    public PostLoansLoanIdTransactionsResponse reverseLoanTransaction(final Long loanId, final Long transactionId,
            final PostLoansLoanIdTransactionsTransactionIdRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId, transactionId, request, "undo"));
    }

    public PostLoansLoanIdTransactionsResponse reverseLoanTransaction(final Long loanId, final Long transactionId, String date) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.adjustLoanTransaction(loanId, transactionId,
                new PostLoansLoanIdTransactionsTransactionIdRequest().dateFormat(DATE_FORMAT).transactionDate(date).transactionAmount(0.0)
                        .locale("en"),
                "undo"));
    }

    public PostLoansLoanIdChargesResponse addLoanCharge(final Long loanId, final PostLoansLoanIdChargesRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.createOrPayLoanCharge(loanId, request, ""));
    }

    public PostLoansLoanIdChargesResponse addLoanCharge(final String loanExternalId, final PostLoansLoanIdChargesRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeByLoanExternalId(loanExternalId, request, ""));
    }

    public PostLoansLoanIdChargesResponse addChargesForLoan(final Long loanId, PostLoansLoanIdChargesRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.createOrPayLoanCharge(loanId, request, null));
    }

    public PutLoansLoanIdChargesChargeIdResponse updateLoanCharge(final Long loanId, final Long loanChargeId,
            final PutLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.updateLoanCharge(loanId, loanChargeId, request));
    }

    public PutLoansLoanIdChargesChargeIdResponse updateLoanCharge(final Long loanId, final String loanChargeExternalId,
            final PutLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.updateLoanChargeByChargeExternalId(loanId,
                loanChargeExternalId, request));
    }

    public PutLoansLoanIdChargesChargeIdResponse updateLoanCharge(final String loanExternalId, final Long loanChargeId,
            final PutLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.updateLoanChargeByLoanExternalId(loanExternalId, loanChargeId,
                request));
    }

    public PutLoansLoanIdChargesChargeIdResponse updateLoanCharge(final String loanExternalId, final String loanChargeExternalId,
            final PutLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.updateLoanChargeByLoanAndChargeExternalId(loanExternalId,
                loanChargeExternalId, request));
    }

    public DeleteLoansLoanIdChargesChargeIdResponse deleteLoanCharge(final Long loanId, final Long loanChargeId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.deleteLoanCharge(loanId, loanChargeId));
    }

    public DeleteLoansLoanIdChargesChargeIdResponse deleteLoanCharge(final Long loanId, final String loanChargeExternalId) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanCharges.deleteLoanChargeByChargeExternalId(loanId, loanChargeExternalId));
    }

    public DeleteLoansLoanIdChargesChargeIdResponse deleteLoanCharge(final String loanExternalId, final Long loanChargeId) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanCharges.deleteLoanChargeByLoanExternalId(loanExternalId, loanChargeId));
    }

    public DeleteLoansLoanIdChargesChargeIdResponse deleteLoanCharge(final String loanExternalId, final String loanChargeExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.deleteLoanChargeByLoanAndChargeExternalId(loanExternalId,
                loanChargeExternalId));
    }

    public PostLoansLoanIdChargesChargeIdResponse chargeAdjustment(final Long loanId, final Long chargeId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeOnExistingCharge(loanId, chargeId, request,
                "adjustment"));
    }

    public PostLoansLoanIdChargesChargeIdResponse chargeAdjustment(final String loanExternalId, final String loanChargeExternalId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeByLoanAndChargeExternalId(loanExternalId,
                loanChargeExternalId, request, "adjustment"));
    }

    public PostLoansLoanIdChargesChargeIdResponse payLoanCharge(final Long loanId, final Long loanChargeId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeOnExistingCharge(loanId, loanChargeId,
                request, "pay"));
    }

    public PostLoansLoanIdChargesChargeIdResponse payLoanCharge(final String loanExternalId, final Long loanChargeId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges
                .executeLoanChargeByLoanExternalIdOnExistingCharge(loanExternalId, loanChargeId, request, "pay"));
    }

    public PostLoansLoanIdChargesChargeIdResponse payLoanCharge(final String loanExternalId, final String loanChargeExternalId,
            final PostLoansLoanIdChargesChargeIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.executeLoanChargeByLoanAndChargeExternalId(loanExternalId,
                loanChargeExternalId, request, "pay"));
    }

    public GetLoansLoanIdChargesChargeIdResponse getLoanCharge(final Long loanId, final Long loanChargeId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.retrieveOneLoanCharge(loanId, loanChargeId));
    }

    public GetLoansLoanIdChargesChargeIdResponse getLoanCharge(final String loanExternalId, final Long loanChargeId) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanCharges.retrieveOneLoanChargeByLoanExternalId(loanExternalId, loanChargeId));
    }

    public GetLoansLoanIdChargesChargeIdResponse getLoanCharge(final Long loanId, final String loanChargeExternalId) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanCharges.retrieveOneLoanChargeByChargeExternalId(loanId, loanChargeExternalId));
    }

    public GetLoansLoanIdChargesChargeIdResponse getLoanCharge(final String loanExternalId, final String loanChargeExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCharges.retrieveOneLoanChargeByLoanAndChargeExternalId(loanExternalId,
                loanChargeExternalId));
    }

    public GetLoansLoanIdTransactionsTransactionIdResponse getLoanTransactionDetails(final Long loanId, final Long transactionId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveOneLoanTransaction(loanId, transactionId, null));
    }

    public GetLoansLoanIdTransactionsTransactionIdResponse getLoanTransactionDetails(final String loanExternalId,
            final Long transactionId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveOneLoanTransactionByLoanExternalId(loanExternalId,
                transactionId, null));
    }

    public GetLoansLoanIdTransactionsTransactionIdResponse getLoanTransactionDetails(final Long loanId,
            final String transactionExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveOneLoanTransactionByExternalId(loanId,
                transactionExternalId, null));
    }

    public GetLoansLoanIdTransactionsTransactionIdResponse getLoanTransactionDetails(final String loanExternalId,
            final String transactionExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .retrieveOneLoanTransactionByLoanExternalIdAndTransactionExternalId(loanExternalId, transactionExternalId, null));
    }

    public GetLoansLoanIdResponse getLoanDetails(final Long loanId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.retrieveOneLoan(loanId, false, "all", null, null));
    }

    public GetLoansLoanIdResponse getLoanDetails(final String loanExternalId) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loans.retrieveOneLoanByExternalId(loanExternalId, false, "all", null, null));
    }

    public GetLoansLoanIdTransactionsResponse getLoanTransactions(final Long loanId) {
        return getLoanTransactions(loanId, Collections.emptyList(), null, null, null);
    }

    public GetLoansLoanIdTransactionsResponse getLoanTransactions(final Long loanId, List<TransactionType> excludedTransactionTypes) {
        return getLoanTransactions(loanId, excludedTransactionTypes, null, null, null);
    }

    public GetLoansLoanIdTransactionsResponse getLoanTransactions(final Long loanId, List<TransactionType> excludedTransactionTypes,
            Integer page, Integer size, String sort) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveAllLoanTransactions(loanId,
                excludedTransactionTypes, page, size, sort));
    }

    public GetLoansLoanIdTransactionsResponse getLoanTransactionsByExternalId(final String loanExternalId) {
        return getLoanTransactionsByExternalId(loanExternalId, Collections.emptyList(), null, null, null);
    }

    public GetLoansLoanIdTransactionsResponse getLoanTransactionsByExternalId(final String loanExternalId,
            List<TransactionType> excludedTransactionTypes) {
        return getLoanTransactionsByExternalId(loanExternalId, excludedTransactionTypes, null, null, null);
    }

    public GetLoansLoanIdTransactionsResponse getLoanTransactionsByExternalId(final String loanExternalId,
            List<TransactionType> excludedTransactionTypes, Integer page, Integer size, String sort) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveAllLoanTransactionsByExternalId(loanExternalId,
                excludedTransactionTypes, page, size, sort));
    }

    /**
     * Helper method to create manual interest refund transaction
     */
    public PostLoansLoanIdTransactionsResponse createManualInterestRefund(Long loanId, Long targetTransactionId, String transactionDate,
            Double amount, String externalId) {

        PostLoansLoanIdTransactionsTransactionIdRequest request = new PostLoansLoanIdTransactionsTransactionIdRequest()
                .transactionAmount(amount).dateFormat("dd MMMM yyyy").locale("en");

        if (externalId != null) {
            request.externalId(externalId);
        }

        return manualInterestRefund(loanId, targetTransactionId, request);
    }

    public GetLoansResponse retrieveAllLoans(final String accountNumber, final String associations, final Long clientId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.retrieveAllLoans(null, 0, 10, null, null, accountNumber,
                associations, clientId, null));
    }

    public GetLoansLoanIdTransactionsTemplateResponse getPrepaymentAmount(final Long loanId, final String transactionDate,
            String dateformat) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveTemplateLoanTransaction(loanId, "prepayLoan",
                dateformat, transactionDate, "en", null));
    }

    public CommandProcessingResult addAndDeleteDisbursementDetail(final Long loanId, PostAddAndDeleteDisbursementDetailRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanDisbursementDetails.addAndDeleteDisbursementDetail(loanId, request));
    }

    public CommandProcessingResult addAndDeleteDisbursementDetail(final Long loanId, final List<DisbursementDetail> disbursementDetails) {
        return addAndDeleteDisbursementDetail(loanId, new PostAddAndDeleteDisbursementDetailRequest().locale("en")
                .dateFormat("dd MMMM yyyy").disbursementData(disbursementDetails));
    }

    public void printRepaymentSchedule(GetLoansLoanIdResponse getLoansLoanIdResponse) {
        GetLoansLoanIdRepaymentSchedule getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
        if (getLoanRepaymentSchedule != null) {
            log.info("Loan with {} periods", getLoanRepaymentSchedule.getPeriods().size());
            for (GetLoansLoanIdRepaymentPeriod period : getLoanRepaymentSchedule.getPeriods()) {
                log.info("Period number {} for due date {} and outstanding {} {}", period.getPeriod(), period.getDueDate(),
                        period.getTotalOutstandingForPeriod(), period.getComplete());
            }
        }
    }

    public void printDelinquencyData(GetLoansLoanIdResponse getLoansLoanIdResponse) {
        GetLoansLoanIdDelinquencySummary getLoansLoanIdCollectionData = getLoansLoanIdResponse.getDelinquent();
        if (getLoansLoanIdCollectionData != null) {
            log.info("Loan Delinquency {}", getLoansLoanIdCollectionData);
        }
    }

    public void evaluateLoanTransactionData(GetLoansLoanIdResponse getLoansLoanIdResponse, String transactionType, Double amountExpected) {
        List<GetLoansLoanIdTransactions> transactions = getLoansLoanIdResponse.getTransactions();
        log.info("Loan with {} transactions", transactions.size());
        Double transactionsAmount = 0.0;
        for (GetLoansLoanIdTransactions transaction : transactions) {
            log.info("  Id {} code {} date {} amount {}", transaction.getId(), transaction.getType().getCode(), transaction.getDate(),
                    transaction.getAmount());
            if (transactionType.equals(transaction.getType().getCode())) {
                transactionsAmount += Utils.getDoubleValue(transaction.getAmount());
            }
        }
        assertEquals(amountExpected, transactionsAmount);
    }

    public Long evaluateLastLoanTransactionData(GetLoansLoanIdResponse getLoansLoanIdResponse, String transactionType,
            String transactionExpected, Double amountExpected) {
        List<GetLoansLoanIdTransactions> transactions = getLoansLoanIdResponse.getTransactions();
        log.info("Loan with {} transactions", transactions.size());
        GetLoansLoanIdTransactions lastTransaction = null;
        for (GetLoansLoanIdTransactions transaction : transactions) {
            log.info("  Id {} code {} date {} amount {}", transaction.getId(), transaction.getType().getCode(), transaction.getDate(),
                    transaction.getAmount());
            if (transactionType.equals(transaction.getType().getCode())) {
                lastTransaction = transaction;
            }
        }
        assertEquals(transactionExpected, Utils.dateFormatter.format(lastTransaction.getDate()));
        assertEquals(amountExpected, Utils.getDoubleValue(lastTransaction.getAmount()));
        return lastTransaction.getId();
    }

    public void validateLoanStatus(GetLoansLoanIdResponse getLoansLoanIdResponse, final String statusCodeExpected) {
        final String statusCode = getLoansLoanIdResponse.getStatus().getCode();
        log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);
        assertEquals(statusCodeExpected, statusCode);
    }

    public void validateLoanPrincipalOustandingBalance(GetLoansLoanIdResponse getLoansLoanIdResponse, Double amountExpected) {
        GetLoansLoanIdSummary getLoansLoanIdSummary = getLoansLoanIdResponse.getSummary();
        if (getLoansLoanIdSummary != null) {
            log.info("Loan with Principal Outstanding Balance {} expected {}", getLoansLoanIdSummary.getPrincipalOutstanding(),
                    amountExpected);
            assertEquals(amountExpected, Utils.getDoubleValue(getLoansLoanIdSummary.getPrincipalOutstanding()));
        }
    }

    public void validateLoanFeesOustandingBalance(GetLoansLoanIdResponse getLoansLoanIdResponse, Double amountExpected) {
        GetLoansLoanIdSummary getLoansLoanIdSummary = getLoansLoanIdResponse.getSummary();
        if (getLoansLoanIdSummary != null) {
            log.info("Loan with Fees Outstanding Balance {} expected {}", getLoansLoanIdSummary.getFeeChargesOutstanding(), amountExpected);
            assertEquals(amountExpected, Utils.getDoubleValue(getLoansLoanIdSummary.getFeeChargesOutstanding()));
        }
    }

    public void validateLoanPenaltiesOustandingBalance(GetLoansLoanIdResponse getLoansLoanIdResponse, Double amountExpected) {
        GetLoansLoanIdSummary getLoansLoanIdSummary = getLoansLoanIdResponse.getSummary();
        assertNotNull(getLoansLoanIdSummary);
        log.info("Loan with Fees Outstanding Balance {} expected {}", getLoansLoanIdSummary.getFeeChargesOutstanding(), amountExpected);
        assertEquals(amountExpected, Utils.getDoubleValue(getLoansLoanIdSummary.getPenaltyChargesOutstanding()));
    }

    public void validateLoanTotalOustandingBalance(GetLoansLoanIdResponse getLoansLoanIdResponse, Double amountExpected) {
        GetLoansLoanIdSummary getLoansLoanIdSummary = getLoansLoanIdResponse.getSummary();
        if (getLoansLoanIdSummary != null) {
            log.info("Loan with Total Outstanding Balance {} expected {}", getLoansLoanIdSummary.getTotalOutstanding(), amountExpected);
            assertEquals(amountExpected, Utils.getDoubleValue(getLoansLoanIdSummary.getTotalOutstanding()));
        }
    }

    public void evaluateLoanDisbursementDetails(GetLoansLoanIdResponse getLoansLoanIdResponse, Integer numItems, Double amountExpected) {
        log.info("Disbursement details items: {}", getLoansLoanIdResponse.getDisbursementDetails().size());
        assertEquals(numItems, getLoansLoanIdResponse.getDisbursementDetails().size());
        Double amount = Double.valueOf("0.0");
        for (GetLoansLoanIdDisbursementDetails disbursementDetails : getLoansLoanIdResponse.getDisbursementDetails()) {
            amount = amount + disbursementDetails.getPrincipal().doubleValue();
            log.info("Disbursement details with principal {} {}", disbursementDetails.getExpectedDisbursementDate(),
                    disbursementDetails.getPrincipal());
        }
        assertEquals(amountExpected, amount);
    }

    public void reviewLoanTransactionRelations(final Integer loanId, final Long transactionId, final Integer expectedSize) {
        GetLoansLoanIdTransactionsTransactionIdResponse getLoansTransactionResponse = getLoanTransactionDetails(loanId.longValue(),
                transactionId);
        assertNotNull(getLoansTransactionResponse);
        assertNotNull(getLoansTransactionResponse.getTransactionRelations());
        assertEquals(expectedSize, getLoansTransactionResponse.getTransactionRelations().size());
        log.info("Loan with {} Chargeback Transactions", getLoansTransactionResponse.getTransactionRelations().size());
    }

    public void evaluateLoanSummaryAdjustments(GetLoansLoanIdResponse getLoansLoanIdResponse, Double amountExpected) {
        // Evaluate The Loan Summary Principal Adjustments
        GetLoansLoanIdSummary getLoansLoanIdSummary = getLoansLoanIdResponse.getSummary();
        if (getLoansLoanIdSummary != null) {
            log.info("Loan with Principal Adjustments {} expected {}", getLoansLoanIdSummary.getPrincipalAdjustments(), amountExpected);
            assertEquals(amountExpected, Utils.getDoubleValue(getLoansLoanIdSummary.getPrincipalAdjustments()));
        }
    }

    public GetLoansLoanIdTransactionsTemplateResponse retrieveTransactionTemplate(Long loanId, String command, String dateFormat,
            String transactionDate, String locale, Long transactionId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveTemplateLoanTransaction(loanId, command,
                dateFormat, transactionDate, locale, transactionId));
    }

    public GetLoansLoanIdTransactionsTemplateResponse retrieveTransactionTemplate(Long loanId, String command, String dateFormat,
            String transactionDate, String locale) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.retrieveTemplateLoanTransaction(loanId, command,
                dateFormat, transactionDate, locale, null));
    }

    public GetLoansLoanIdTransactionsTemplateResponse retrieveTransactionTemplate(String loanExternalIdStr, String command,
            String dateFormat, String transactionDate, String locale) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .retrieveTemplateLoanTransactionByLoanExternalId(loanExternalIdStr, command, dateFormat, transactionDate, locale, null));
    }

    public GetLoansApprovalTemplateResponse getLoanApprovalTemplate(String loanExternalIdStr) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.retrieveApprovalTemplateByExternalId(loanExternalIdStr, "approval"));
    }

    public DeleteLoansLoanIdResponse deleteLoanApplication(String loanExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.deleteLoanApplicationByExternalId(loanExternalId));
    }

    public List<GetDelinquencyTagHistoryResponse> getLoanDelinquencyTags(String loanExternalId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.retrieveDelinquencyTagHistoryLoanByExternalId(loanExternalId));
    }

    public PostLoansResponse applyLoan(PostLoansRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.calculateOrSubmitLoanApplication(request, null));
    }

    public void applyLoanWithError(PostLoansRequest request, Integer httpStatus) {
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> Calls.ok(FineractClientHelper.getFineractClient().loans.calculateOrSubmitLoanApplication(request, null)));
        assertEquals(exception.getResponse().code(), httpStatus);
    }

    public PostLoansLoanIdResponse approveLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request, "approve"));
    }

    public PostLoansLoanIdResponse approveLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "approve"));
    }

    public PostLoansLoanIdResponse rejectLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request, "reject"));
    }

    public PostLoansLoanIdResponse rejectLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "reject"));
    }

    public PostLoansLoanIdResponse withdrawnByApplicantLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "withdrawnByApplicant"));
    }

    public PostLoansLoanIdResponse withdrawnByApplicantLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "withdrawnByApplicant"));
    }

    public PostLoansLoanIdResponse disburseLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request, "disburse"));
    }

    public PostLoansLoanIdResponse disburseLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "disburse"));
    }

    public PostLoansLoanIdResponse moveLoanState(Long loanId, PostLoansLoanIdRequest request, String command) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, command));
    }

    /**
     * Disburse loan on provided date and amount.
     *
     * @param loanId
     *            loan Id
     * @param date
     *            formatted to "d MMMM yyyy"
     * @param amount
     *            amount to disburse
     * @return Post Loans Loan Id Response
     */
    public PostLoansLoanIdResponse disburseLoan(Long loanId, String date, Double amount) {
        return disburseLoan(loanId, new PostLoansLoanIdRequest().actualDisbursementDate(date).dateFormat(DATE_FORMAT)
                .transactionAmount(BigDecimal.valueOf(amount)).locale("en"));
    }

    public PostLoansLoanIdResponse disburseToSavingsLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "disburseToSavings"));
    }

    public PostLoansLoanIdResponse undoApprovalLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request, "undoapproval"));
    }

    public PostLoansLoanIdResponse undoDisbursalLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request, "undodisbursal"));
    }

    public PostLoansLoanIdResponse undoDisbursalLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "undodisbursal"));
    }

    public PostLoansLoanIdResponse undoLastDisbursalLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "undolastdisbursal"));
    }

    public PostLoansLoanIdResponse undoLastDisbursalLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "undolastdisbursal"));
    }

    public PostLoansLoanIdResponse assignLoanOfficerLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "assignloanofficer"));
    }

    public PostLoansLoanIdResponse unassignLoanOfficerLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "unassignloanofficer"));
    }

    public PostLoansLoanIdResponse recoverGuaranteesLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "recoverGuarantees"));
    }

    public PostLoansLoanIdResponse assignDelinquencyLoan(String loanExternalId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoanByExternalId(loanExternalId, request,
                "assigndelinquency"));
    }

    public PostLoansLoanIdTransactionsResponse closeRescheduledLoan(String loanExternalId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "close-rescheduled"));
    }

    public PostLoansLoanIdTransactionsResponse closeRescheduledLoan(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "close-rescheduled"));
    }

    public PostLoansLoanIdTransactionsResponse closeLoan(String loanExternalId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "close"));
    }

    public PostLoansLoanIdTransactionsResponse closeLoan(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "close"));
    }

    public PostLoansLoanIdTransactionsResponse forecloseLoan(String loanExternalId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "foreclosure"));
    }

    public PostLoansLoanIdTransactionsResponse forecloseLoan(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "foreclosure"));
    }

    public PostLoansLoanIdTransactionsResponse chargeOffLoan(String loanExternalId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "charge-off"));
    }

    public PostLoansLoanIdTransactionsResponse chargeOffLoan(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "charge-off"));
    }

    public PostLoansLoanIdTransactionsResponse undoChargeOffLoan(String loanExternalId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "undo-charge-off"));
    }

    public PostLoansLoanIdTransactionsResponse undoChargeOffLoan(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request,
                "undo-charge-off"));
    }

    public List<CapitalizedIncomeDetails> fetchCapitalizedIncomeDetails(Long loanId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanCapitalizedIncome.fetchCapitalizedIncomeDetails(loanId));
    }

    public static List<Long> getLoanIdsByStatusId(Integer statusId) {
        return Calls.ok(FineractClientHelper.getFineractClient().legacy.getLoansByStatus(statusId));
    }

    public PutLoanProductsProductIdResponse updateLoanProduct(Long id, PutLoanProductsProductIdRequest requestModifyLoan) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanProducts.updateLoanProduct(id, requestModifyLoan));
    }

    public PostLoanProductsResponse createLoanProduct(PostLoanProductsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanProducts.createLoanProduct(request));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanDownPayment(String loanExternalId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "downPayment"));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanDownPayment(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(
                FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "downPayment"));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanBuyDownFee(Long loanId, PostLoansLoanIdTransactionsRequest request) {
        return Calls
                .ok(FineractClientHelper.getFineractClient().loanTransactions.handleCommandsLoanTransaction(loanId, request, "buyDownFee"));
    }

    public PostLoansLoanIdTransactionsResponse makeLoanBuyDownFee(Long loanId, String date, double amount) {
        return makeLoanBuyDownFee(loanId, new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate(date)
                .locale("en").transactionAmount(amount));
    }

    public List<AdvancedPaymentData> getAdvancedPaymentAllocationRules(final Integer loanId) {
        return Calls.ok(FineractClientHelper.getFineractClient().legacy.getAdvancedPaymentAllocationRulesOfLoan(loanId.longValue()));
    }

    public PostLoansLoanIdTransactionsResponse writeOffLoanAccount(final String loanExternalId,
            final PostLoansLoanIdTransactionsRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanTransactions
                .handleCommandsLoanTransactionByLoanExternalId(loanExternalId, request, "writeoff"));
    }

    public PostLoansLoanIdResponse undoApprovalForLoan(Long loanId, PostLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.handleCommandsLoan(loanId, request, "undoapproval"));
    }

    public PutLoansLoanIdResponse modifyApplicationForLoan(final Long loanId, final String command, final PutLoansLoanIdRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.updateLoanApplication(loanId, request, command));
    }

    public PostLoansResponse calculateRepaymentScheduleForApplyLoan(PostLoansRequest request, String command) {
        return Calls.ok(FineractClientHelper.getFineractClient().loans.calculateOrSubmitLoanApplication(request, command));
    }

    public List<BuyDownFeeAmortizationDetails> fetchBuyDownFeeAmortizationDetails(Long loanId) {
        return Calls.ok(FineractClientHelper.getFineractClient().loanBuyDownFeesApi.retrieveLoanBuyDownFeeAmortizationDetails(loanId));
    }
}
