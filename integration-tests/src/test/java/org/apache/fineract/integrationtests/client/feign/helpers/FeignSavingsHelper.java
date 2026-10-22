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
package org.apache.fineract.integrationtests.client.feign.helpers;

import static org.apache.fineract.client.feign.util.FeignCalls.fail;
import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import com.fasterxml.jackson.core.type.TypeReference;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.FineractMultipartEncoder;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.DeleteSavingsAccountsAccountIdResponse;
import org.apache.fineract.client.models.DepositAccountOnHoldTransactionData;
import org.apache.fineract.client.models.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.client.models.GetSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdResponse;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.client.models.PostSavingsAccountsRequest;
import org.apache.fineract.client.models.PostSavingsAccountsResponse;
import org.apache.fineract.client.models.PostSavingsAccountsSavingsAccountIdChargesRequest;
import org.apache.fineract.client.models.PostSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.client.models.PostSavingsProductsRequest;
import org.apache.fineract.client.models.PutSavingsAccountsAccountIdRequest;
import org.apache.fineract.client.models.PutSavingsAccountsAccountIdResponse;
import org.apache.fineract.client.models.SavingsAccountChargeData;
import org.apache.fineract.client.models.SavingsAccountData;
import org.apache.fineract.client.models.SavingsAccountStatusEnumData;
import org.apache.fineract.client.models.SavingsAccountSubStatusEnumData;
import org.apache.fineract.client.models.SavingsAccountSummaryData;
import org.apache.fineract.integrationtests.client.feign.modules.SavingsRequestBuilders;
import org.apache.fineract.integrationtests.client.feign.modules.SavingsTestData;
import org.apache.fineract.integrationtests.client.feign.modules.SavingsTestValidators;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;

public class FeignSavingsHelper {

    private static final String DATATABLE_DATE_TIME_FORMAT = "dd MMMM yyyy HH:mm";
    private static final String IMPORT_LOCALE = "en";
    private static final String IMPORT_DATE_FORMAT = "dd MMMM yyyy";

    private final FineractFeignClient fineractClient;

    public FeignSavingsHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public PostSavingsAccountsResponse submitApplication(PostSavingsAccountsRequest request) {
        return ok(() -> fineractClient.savingsAccount().submitSavingsApplication(request));
    }

    public PostSavingsAccountsResponse submitApplication(Long clientId, Long productId, String submittedOnDate) {
        return submitApplication(SavingsRequestBuilders.submitSavingsApplication(clientId, productId, submittedOnDate));
    }

    public CallFailedRuntimeException submitApplicationExpectingError(Long clientId, Long productId, String submittedOnDate) {
        PostSavingsAccountsRequest request = SavingsRequestBuilders.submitSavingsApplication(clientId, productId, submittedOnDate);
        return fail(() -> fineractClient.savingsAccount().submitSavingsApplication(request));
    }

    /**
     * Submits the application together with one entry for the named datatable, as an entity datatable check demands.
     */
    public PostSavingsAccountsResponse submitApplicationWithDatatable(Long clientId, Long productId, String submittedOnDate,
            String registeredTableName) {
        Map<String, Object> application = ObjectMapperFactory.getShared().convertValue(
                SavingsRequestBuilders.submitSavingsApplication(clientId, productId, submittedOnDate), new TypeReference<>() {});
        application.put("datatables", List.of(datatableEntry(registeredTableName)));
        SavingsApplicationWithDatatablesApi api = fineractClient.create(SavingsApplicationWithDatatablesApi.class);
        return ok(() -> api.submitSavingsApplication(application));
    }

    private static Map<String, Object> datatableEntry(String registeredTableName) {
        Map<String, Object> data = new HashMap<>();
        data.put("locale", SavingsTestData.LOCALE);
        data.put("Spouse Name", Utils.randomStringGenerator("Spouse_name", 4));
        data.put("Number of Dependents", 5);
        data.put("Time of Visit", "01 December 2016 04:03");
        data.put("dateFormat", DATATABLE_DATE_TIME_FORMAT);
        data.put("Date of Approval", "02 December 2016 00:00");
        Map<String, Object> entry = new HashMap<>();
        entry.put("registeredTableName", registeredTableName);
        entry.put("data", data);
        return entry;
    }

    /**
     * Creates a daily-compounding, monthly-posting product with the given minimum opening balance and takes a new
     * account on it through submit, approve and activate on the legacy helper's fixed dates.
     */
    public Long openSavingsAccount(Long clientId, String minimumOpeningBalance) {
        PostSavingsProductsRequest product = SavingsRequestBuilders
                .savingsProduct(SavingsTestData.InterestCompoundingPeriodType.DAILY, SavingsTestData.InterestPostingPeriodType.MONTHLY,
                        SavingsTestData.InterestCalculationType.DAILY_BALANCE)
                .minRequiredOpeningBalance(new BigDecimal(minimumOpeningBalance));
        Long productId = ok(() -> fineractClient.savingsProduct().createSavingsProduct(product)).getResourceId();
        return openSavingsOnLegacyDates(clientId, productId);
    }

    /**
     * Submits, approves and activates an account on the fixed 2013 dates the legacy helper used, checking status at
     * each step.
     */
    public Long openSavingsOnLegacyDates(Long clientId, Long productId) {
        Long savingsId = submitApplication(clientId, productId, SavingsTestData.CREATED_DATE).getSavingsId();
        SavingsTestValidators.verifySavingsIsPending(getSavingsStatus(savingsId));
        approveSavings(savingsId, SavingsTestData.CREATED_DATE_PLUS_ONE);
        SavingsTestValidators.verifySavingsIsApproved(getSavingsStatus(savingsId));
        activateSavings(savingsId, SavingsTestData.TRANSACTION_DATE);
        SavingsTestValidators.verifySavingsIsActive(getSavingsStatus(savingsId));
        return savingsId;
    }

    public Workbook getSavingsWorkbook(String dateFormat) {
        return workbook(fineractClient.create(SavingsBulkImportApi.class).downloadTemplate(dateFormat));
    }

    /** Returns the id of the import document the server tracks the upload under. */
    public String importSavingsTemplate(File file) {
        try {
            FineractMultipartEncoder.MultipartData multipartData = new FineractMultipartEncoder.MultipartData()
                    .addFile("file", file.getName(), Files.readAllBytes(file.toPath()), "application/vnd.ms-excel")
                    .addText("locale", IMPORT_LOCALE).addText("dateFormat", IMPORT_DATE_FORMAT);
            return ok(() -> fineractClient.create(SavingsBulkImportApi.class).uploadTemplate(multipartData));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    public byte[] downloadOutputTemplate(String importDocumentId) {
        return bytes(fineractClient.create(SavingsBulkImportApi.class).downloadOutputTemplate(importDocumentId));
    }

    private static Workbook workbook(Response response) {
        try {
            return new HSSFWorkbook(new ByteArrayInputStream(bytes(response)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the workbook", e);
        }
    }

    /** A raw {@link Response} bypasses the error decoder, so the status has to be checked by hand. */
    private static byte[] bytes(Response response) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("HTTP " + response.status() + " from " + response.request().url());
        }
        try (InputStream inputStream = response.body().asInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the response body", e);
        }
    }

    public PostSavingsAccountsResponse submitGroupApplication(Long groupId, Long productId, String submittedOnDate) {
        return submitApplication(SavingsRequestBuilders.submitGroupSavingsApplication(groupId, productId, submittedOnDate));
    }

    public PutSavingsAccountsAccountIdResponse updateGroupSavingsApplication(Long savingsId, Long groupId, Long productId,
            String submittedOnDate) {
        return updateSavingsAccount(savingsId, SavingsRequestBuilders.updateGroupSavingsApplication(groupId, productId, submittedOnDate));
    }

    public PostSavingsAccountsAccountIdResponse approveSavings(Long savingsId, String approvedOnDate) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.approveSavings(approvedOnDate);
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "approve"));
    }

    public PostSavingsAccountsAccountIdResponse undoApproval(Long savingsId) {
        PostSavingsAccountsAccountIdRequest request = new PostSavingsAccountsAccountIdRequest();
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "undoApproval"));
    }

    public PostSavingsAccountsAccountIdResponse activateSavings(Long savingsId, String activatedOnDate) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.activateSavings(activatedOnDate);
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "activate"));
    }

    public PostSavingsAccountsAccountIdResponse rejectSavings(Long savingsId, String rejectedOnDate) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.rejectSavings(rejectedOnDate);
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "reject"));
    }

    public CallFailedRuntimeException closeSavingsExpectingError(Long savingsId, String closedOnDate, boolean withdrawBalance) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.closeSavings(closedOnDate, withdrawBalance);
        return fail(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "close"));
    }

    /** Closing with this flag on makes the server refuse the close unless interest has already been posted. */
    public CallFailedRuntimeException closeSavingsValidatingPostedInterestExpectingError(Long savingsId, String closedOnDate,
            boolean withdrawBalance) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.closeSavings(closedOnDate, withdrawBalance)
                .postInterestValidationOnClosure(true);
        return fail(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "close"));
    }

    public PostSavingsAccountsAccountIdResponse closeSavingsValidatingPostedInterest(Long savingsId, String closedOnDate,
            boolean withdrawBalance) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.closeSavings(closedOnDate, withdrawBalance)
                .postInterestValidationOnClosure(true);
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "close"));
    }

    public CallFailedRuntimeException rejectSavingsExpectingError(Long savingsId, String rejectedOnDate) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.rejectSavings(rejectedOnDate);
        return fail(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "reject"));
    }

    public CallFailedRuntimeException deleteSavingsApplicationExpectingError(Long savingsId) {
        return fail(() -> fineractClient.savingsAccount().deleteSavingsAccount(savingsId));
    }

    public CallFailedRuntimeException getSavingsDetailsExpectingError(Long savingsId) {
        return fail(() -> fineractClient.savingsAccount().retrieveSavingsAccount(savingsId, Map.of()));
    }

    public PostSavingsAccountsAccountIdResponse closeSavings(Long savingsId, String closedOnDate, boolean withdrawBalance) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.closeSavings(closedOnDate, withdrawBalance);
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "close"));
    }

    public SavingsAccountData getSavingsDetails(Long savingsId) {
        return ok(() -> fineractClient.savingsAccount().retrieveSavingsAccount(savingsId, Map.of("associations", "all")));
    }

    public PostSavingsAccountsAccountIdResponse commandByExternalId(String externalId, PostSavingsAccountsAccountIdRequest request,
            String command) {
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccountByExternalId(externalId, request, command));
    }

    public PostSavingsAccountsAccountIdResponse approveSavingsByExternalId(String externalId, String approvedOnDate) {
        return commandByExternalId(externalId, SavingsRequestBuilders.approveSavings(approvedOnDate), "approve");
    }

    public PostSavingsAccountsAccountIdResponse undoApprovalByExternalId(String externalId) {
        return commandByExternalId(externalId, new PostSavingsAccountsAccountIdRequest(), "undoapproval");
    }

    public PutSavingsAccountsAccountIdResponse updateSavingsByExternalId(String externalId, PutSavingsAccountsAccountIdRequest request) {
        return ok(() -> fineractClient.savingsAccount().updateSavingsAccountByExternalId(externalId, request, ""));
    }

    public SavingsAccountData getSavingsDetailsByExternalId(String externalId, String associations) {
        return ok(() -> fineractClient.savingsAccount().retrieveSavingsAccountByExternalId(externalId, null, null, associations));
    }

    public CallFailedRuntimeException getSavingsDetailsByExternalIdExpectingError(String externalId) {
        return fail(() -> fineractClient.savingsAccount().retrieveSavingsAccountByExternalId(externalId, null, null, "all"));
    }

    public DeleteSavingsAccountsAccountIdResponse deleteSavingsByExternalId(String externalId) {
        return ok(() -> fineractClient.savingsAccount().deleteSavingsAccountByExternalId(externalId));
    }

    /**
     * The block commands carry a reason; their unblock counterparts do not, which is why they take different bodies.
     */
    public PostSavingsAccountsAccountIdResponse blockSavings(Long savingsId, String reasonForBlock) {
        return command(savingsId, blockRequest(reasonForBlock), "block");
    }

    public PostSavingsAccountsAccountIdResponse unblockSavings(Long savingsId) {
        return command(savingsId, unblockRequest(), "unblock");
    }

    public PostSavingsAccountsAccountIdResponse blockCredit(Long savingsId, String reasonForBlock) {
        return command(savingsId, blockRequest(reasonForBlock), "blockCredit");
    }

    public PostSavingsAccountsAccountIdResponse unblockCredit(Long savingsId) {
        return command(savingsId, unblockRequest(), "unblockCredit");
    }

    public PostSavingsAccountsAccountIdResponse blockDebit(Long savingsId, String reasonForBlock) {
        return command(savingsId, blockRequest(reasonForBlock), "blockDebit");
    }

    public PostSavingsAccountsAccountIdResponse unblockDebit(Long savingsId) {
        return command(savingsId, unblockRequest(), "unblockDebit");
    }

    public PostSavingsAccountsAccountIdResponse command(Long savingsId, PostSavingsAccountsAccountIdRequest request, String command) {
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, command));
    }

    /** Toggles withholding tax on an existing account; the command takes that one field. */
    public PutSavingsAccountsAccountIdResponse updateWithHoldTaxStatus(Long savingsId, boolean withHoldTax) {
        PutSavingsAccountsAccountIdRequest request = new PutSavingsAccountsAccountIdRequest().withHoldTax(withHoldTax);
        return ok(() -> fineractClient.savingsAccount().updateSavingsAccount(savingsId, request, "updateWithHoldTax"));
    }

    private PostSavingsAccountsAccountIdRequest blockRequest(String reasonForBlock) {
        return new PostSavingsAccountsAccountIdRequest()//
                .locale(SavingsTestData.LOCALE)//
                .dateFormat(SavingsTestData.DATETIME_PATTERN)//
                .activatedOnDate(Utils.dateFormatter.format(Utils.getLocalDateOfTenant()))//
                .reasonForBlock(reasonForBlock);
    }

    private PostSavingsAccountsAccountIdRequest unblockRequest() {
        return new PostSavingsAccountsAccountIdRequest()//
                .locale(SavingsTestData.LOCALE)//
                .dateFormat(SavingsTestData.DATETIME_PATTERN)//
                .activatedOnDate(Utils.dateFormatter.format(Utils.getLocalDateOfTenant()));
    }

    public SavingsAccountData getSavingsDetails(Long savingsId, String associations) {
        return ok(() -> fineractClient.savingsAccount().retrieveSavingsAccount(savingsId, Map.of("associations", associations)));
    }

    public SavingsAccountSummaryData getSavingsSummary(Long savingsId) {
        return getSavingsDetails(savingsId, "summary").getSummary();
    }

    public DeleteSavingsAccountsAccountIdResponse deleteSavingsApplication(Long savingsId) {
        return ok(() -> fineractClient.savingsAccount().deleteSavingsAccount(savingsId));
    }

    public PostSavingsAccountsAccountIdResponse withdrawnByApplicant(Long savingsId, String withdrawnOnDate) {
        PostSavingsAccountsAccountIdRequest request = SavingsRequestBuilders.withdrawnByApplicant(withdrawnOnDate);
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "withdrawnByApplicant"));
    }

    public PostSavingsAccountsAccountIdResponse postInterest(Long savingsId) {
        PostSavingsAccountsAccountIdRequest request = new PostSavingsAccountsAccountIdRequest();
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "postInterest"));
    }

    public PostSavingsAccountsAccountIdResponse calculateInterest(Long savingsId) {
        PostSavingsAccountsAccountIdRequest request = new PostSavingsAccountsAccountIdRequest();
        return ok(() -> fineractClient.savingsAccount().handleCommandsSavingsAccount(savingsId, request, "calculateInterest"));
    }

    public PutSavingsAccountsAccountIdResponse updateSavingsAccount(Long savingsId, PutSavingsAccountsAccountIdRequest request) {
        return ok(() -> fineractClient.savingsAccount().updateSavingsAccount(savingsId, request, (String) null));
    }

    /** The status is on the account itself, so no associations are requested. */
    public SavingsAccountStatusEnumData getSavingsStatus(Long savingsId) {
        return ok(() -> fineractClient.savingsAccount().retrieveSavingsAccount(savingsId, Map.of())).getStatus();
    }

    /** The guarantor holds standing against the account, which is what an account pledged as collateral carries. */
    public List<DepositAccountOnHoldTransactionData> getOnHoldTransactions(Long savingsId) {
        return ok(() -> fineractClient.depositAccountOnHoldFundTransactions().retrieveAllDepositAccountOnHoldFundTransactions(savingsId,
                Map.of())).getPageItems();
    }

    public SavingsAccountSubStatusEnumData getSavingsSubStatus(Long savingsId) {
        return ok(() -> fineractClient.savingsAccount().retrieveSavingsAccount(savingsId, Map.of())).getSubStatus();
    }

    /**
     * The charges listing endpoint answers a projection without the due date or the fee interval, so the charges a test
     * has to reason about are read off the account itself.
     */
    public List<SavingsAccountChargeData> getSavingsAccountCharges(Long savingsId) {
        return getSavingsDetails(savingsId, "charges").getCharges();
    }

    public PutSavingsAccountsAccountIdResponse updateSavingsApplication(Long savingsId, Long clientId, Long productId,
            String submittedOnDate) {
        return updateSavingsAccount(savingsId, SavingsRequestBuilders.updateSavingsApplication(clientId, productId, submittedOnDate));
    }

    public Long createApproveActivateSavings(Long clientId, Long productId, String date) {
        Long savingsId = submitApplication(clientId, productId, date).getSavingsId();
        approveSavings(savingsId, date);
        activateSavings(savingsId, date);
        return savingsId;
    }

    public PostSavingsAccountsSavingsAccountIdChargesResponse addSavingsAccountCharge(Long savingsId,
            PostSavingsAccountsSavingsAccountIdChargesRequest request) {
        return ok(() -> fineractClient.savingsCharges().createSavingsAccountCharge(savingsId, request));
    }

    public GetSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdResponse getSavingsAccountCharge(Long savingsId,
            Long savingsAccountChargeId) {
        return ok(() -> fineractClient.savingsCharges().retrieveOneSavingsAccountCharge(savingsId, savingsAccountChargeId));
    }

    public List<GetSavingsAccountsSavingsAccountIdChargesResponse> getSavingsCharges(Long savingsId) {
        return ok(() -> fineractClient.savingsCharges().retrieveAllSavingsAccountCharges(savingsId, "all"));
    }
}
