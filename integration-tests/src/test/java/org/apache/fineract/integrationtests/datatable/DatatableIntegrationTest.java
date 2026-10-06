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
package org.apache.fineract.integrationtests.datatable;

import static org.apache.fineract.integrationtests.client.feign.modules.DatatableRequestBuilders.PERSON_ENTITY_SUB_TYPE;
import static org.apache.fineract.integrationtests.client.feign.modules.DatatableRequestBuilders.column;
import static org.apache.fineract.integrationtests.client.feign.modules.DatatableRequestBuilders.dropdownColumn;
import static org.apache.fineract.integrationtests.client.feign.modules.DatatableRequestBuilders.stringColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.GetDataTablesResponse;
import org.apache.fineract.client.models.PostColumnHeaderData;
import org.apache.fineract.client.models.PostDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PostDataTablesRequest;
import org.apache.fineract.client.models.PostDataTablesResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdDatatableIdResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PutDataTablesRequest;
import org.apache.fineract.client.models.PutDataTablesRequestAddColumns;
import org.apache.fineract.client.models.PutDataTablesRequestChangeColumns;
import org.apache.fineract.client.models.PutDataTablesRequestDropColumns;
import org.apache.fineract.client.models.PutDataTablesResponse;
import org.apache.fineract.client.models.ResultsetColumnHeaderData;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignDatatableHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(LoanTestLifecycleExtension.class)
public class DatatableIntegrationTest extends IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatatableIntegrationTest.class);

    private static final String CLIENT_APP_TABLE_NAME = "m_client";
    private static final String CLIENT_PERSON_SUBTYPE_NAME = "Person";
    private static final String LOAN_APP_TABLE_NAME = "m_loan";
    private static final String VALIDATION_ERRORS_EXIST = "validation.msg.validation.errors.exist";

    private static final Float LP_PRINCIPAL = 10000.0f;
    private static final String LP_REPAYMENTS = "5";
    private static final String LP_REPAYMENT_PERIOD = "2";
    private static final String LP_INTEREST_RATE = "1";
    private static final String EXPECTED_DISBURSAL_DATE = "14 March 2011";
    private static final String LOAN_APPLICATION_SUBMISSION_DATE = "13 March 2011";
    private static final String LOAN_TERM_FREQUENCY = "10";
    private static final String INDIVIDUAL_LOAN = "individual";
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    public static final String MINIMUM_OPENING_BALANCE = "1000.0";
    public static final String DEPOSIT_AMOUNT = "7000";
    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private FeignDatatableHelper datatableHelper;

    private LoanTransactionHelper loanTransactionHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.datatableHelper = new FeignDatatableHelper(FineractFeignClientHelper.getFineractFeignClient());
        this.loanTransactionHelper = new LoanTransactionHelper(requestSpec, responseSpec);
    }

    @Test
    public void validateCreateReadDeleteDatatable() throws ParseException {
        // Fetch / Create tst code
        String tst_tst_tst = "TST_TST_TST".toLowerCase();
        HashMap<String, Object> codeResponse = CodeHelper.getCodeByName(this.requestSpec, this.responseSpec, tst_tst_tst);

        Integer createdCodeId = (Integer) codeResponse.get("id");
        Integer createdCodeValueId;
        Integer createdCodeValueIdSecond;
        if (createdCodeId == null) {
            createdCodeId = (Integer) CodeHelper.createCode(this.requestSpec, this.responseSpec, tst_tst_tst, "resourceId");

            createdCodeValueId = CodeHelper.createCodeValue(this.requestSpec, this.responseSpec, createdCodeId,
                    Utils.randomStringGenerator("cv_", 8), 1);
            createdCodeValueIdSecond = CodeHelper.createCodeValue(this.requestSpec, this.responseSpec, createdCodeId,
                    Utils.randomStringGenerator("cv_", 8), 2);
        } else {
            List<HashMap<String, Object>> codeValuesForCode = CodeHelper.getCodeValuesForCode(this.requestSpec, this.responseSpec,
                    createdCodeId, "");
            createdCodeValueId = (Integer) codeValuesForCode.get(0).get("id");
            createdCodeValueIdSecond = (Integer) codeValuesForCode.get(1).get("id");
        }

        // creating datatable for client entity
        String itsABoolean = "itsaboolean";
        String itsADate = "itsadate";
        String itsADatetime = "itsadatetime";
        String itsADecimal = "itsadecimal";
        String itsADropdown = "itsadropdown";
        String itsANumber = "itsanumber";
        String itsAString = "itsastring";
        String itsAText = "itsatext";
        String itsAJson = "itsajson";
        String tst_tst_tst_cd_itsADropdown = tst_tst_tst + "_cd_itsadropdown";
        String dateFormat = "dateFormat";

        final List<PostColumnHeaderData> datatableColumns = new ArrayList<>();
        datatableColumns.add(column(itsABoolean, "Boolean", false));
        datatableColumns.add(column(itsADate, "Date", true));
        datatableColumns.add(column(itsADatetime, "Datetime", true));
        datatableColumns.add(column(itsADecimal, "Decimal", true));
        datatableColumns.add(dropdownColumn(itsADropdown, tst_tst_tst, false));
        datatableColumns.add(column(itsANumber, "Number", true));
        datatableColumns.add(stringColumn(itsAString, 10L, true));

        final PostDataTablesRequest datatableRequest = new PostDataTablesRequest()
                .datatableName(Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5).toLowerCase())
                .entitySubType(PERSON_ENTITY_SUB_TYPE).multiRow(false).columns(datatableColumns);

        // try to create datatable without apptable
        JsonNode errorResponse = assertFails(400, () -> datatableHelper.createDatatable(datatableRequest));
        assertEquals(VALIDATION_ERRORS_EXIST, errorResponse.get("userMessageGlobalisationCode").asText());
        JsonNode errors = errorResponse.get("errors");
        assertEquals(2, errors.size());
        assertEquals("validation.msg.datatable.apptableName.cannot.be.blank", errors.get(0).get("userMessageGlobalisationCode").asText());
        assertEquals("validation.msg.datatable.apptableName.is.not.one.of.expected.enumerations",
                errors.get(1).get("userMessageGlobalisationCode").asText());

        // set valid apptable name
        datatableRequest.apptableName(CLIENT_APP_TABLE_NAME);

        // try to create datatable with invalid column type
        PostColumnHeaderData textColumn = column(itsAText, "Invalid", true);
        datatableColumns.add(textColumn);
        errorResponse = assertFails(400, () -> datatableHelper.createDatatable(datatableRequest));
        assertEquals(VALIDATION_ERRORS_EXIST, errorResponse.get("userMessageGlobalisationCode").asText());
        errors = errorResponse.get("errors");
        assertEquals(1, errors.size());
        JsonNode error = errors.get(0);
        assertEquals("validation.msg.datatable.type.is.not.one.of.expected.enumerations",
                error.get("userMessageGlobalisationCode").asText());
        assertTrue(error.get("defaultUserMessage").asText()
                .contains("string, number, boolean, decimal, date, datetime, text, json, dropdown"));

        // set valid type
        textColumn.type("Text");
        // add json type
        datatableColumns.add(column(itsAJson, "Json", false));
        LOG.info("request : {}", datatableRequest);

        String datatableName = datatableHelper.createDatatable(datatableRequest).getResourceIdentifier();
        verifyDatatableCreatedOnServer(datatableName);

        // try to create with the same name
        errorResponse = assertFails(400, () -> datatableHelper.createDatatable(datatableRequest));
        assertEquals(VALIDATION_ERRORS_EXIST, errorResponse.get("userMessageGlobalisationCode").asText());

        // creating client with datatables
        final Integer clientID = ClientHelper.createClientAsPerson(requestSpec, responseSpec);

        // creating new client datatable entry
        final boolean genericResultSet = true;

        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put(itsABoolean, Utils.randomNumberGenerator(1) % 2 == 0);
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADatetime, Utils.randomDateTimeGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(tst_tst_tst_cd_itsADropdown, createdCodeValueId);
        datatableEntryMap.put(itsANumber, Utils.randomNumberGenerator(5));
        datatableEntryMap.put(itsAString, Utils.randomStringGenerator("", 8));
        datatableEntryMap.put(itsAText, Utils.randomStringGenerator("", 1000));
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        String json = "{\"testparam\": \"testvalue\"}";
        // add invalid json
        datatableEntryMap.put(itsAJson, '{' + json);

        String invalidEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        assertFails(403, () -> datatableHelper.createDatatableEntry(datatableName, clientID.longValue(), invalidEntryRequestJsonString));

        // add valid json
        datatableEntryMap.put(itsAJson, json);

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PostDataTablesAppTableIdResponse datatableEntryResponse = datatableHelper.createDatatableEntry(datatableName, clientID.longValue(),
                datatabelEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        final Map<String, Object> items = asMap(datatableHelper.getDatatableEntry(datatableName, clientID.longValue(),
                datatableEntryResponse.getResourceId(), genericResultSet, null));
        assertNotNull(items);

        List columnHeaders = (List) items.get("columnHeaders");
        List columnData = (List) items.get("data");
        assertEquals(1, columnData.size());

        Map data = (Map) columnData.get(0);

        assertEquals("client_id", ((Map) columnHeaders.get(0)).get("columnName"));
        assertEquals(clientID, ((List) data.get("row")).get(0));

        assertEquals(itsABoolean, ((Map) columnHeaders.get(1)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsABoolean), ((List) data.get("row")).get(1));

        assertEquals(itsADate, ((Map) columnHeaders.get(2)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADate), Utils.arrayDateToString((List) ((List) data.get("row")).get(2)));

        assertEquals(itsADatetime, ((Map) columnHeaders.get(3)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADatetime), Utils.arrayDateTimeToString((List) ((List) data.get("row")).get(3)));

        assertEquals(itsADecimal, ((Map) columnHeaders.get(4)).get("columnName"));
        assertDecimalEquals(datatableEntryMap.get(itsADecimal), ((List) data.get("row")).get(4));

        assertEquals(tst_tst_tst_cd_itsADropdown, ((Map) columnHeaders.get(5)).get("columnName"));
        assertEquals(datatableEntryMap.get(tst_tst_tst_cd_itsADropdown), ((List) data.get("row")).get(5));

        assertEquals(itsANumber, ((Map) columnHeaders.get(6)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsANumber), ((List) data.get("row")).get(6));

        assertEquals(itsAString, ((Map) columnHeaders.get(7)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsAString), ((List) data.get("row")).get(7));

        assertEquals(itsAText, ((Map) columnHeaders.get(8)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsAText), ((List) data.get("row")).get(8));

        assertEquals(itsAJson, ((Map) columnHeaders.get(9)).get("columnName"));
        Object jsonResponse = ((List) data.get("row")).get(9);
        assertEquals(datatableEntryMap.get(itsAJson), jsonResponse instanceof Map ? ((Map) jsonResponse).get("value") : jsonResponse);

        // Read the Datatable entry generated with genericResultSet in false
        List<Map<String, Object>> datatableEntryResponseNoGenericResult = asList(datatableHelper.getDatatableEntry(datatableName,
                clientID.longValue(), datatableEntryResponse.getResourceId(), !genericResultSet, null));
        assertNotNull(datatableEntryResponseNoGenericResult, "ERROR IN GETTING THE DATE VALUE FROM DATATABLE RECORD");
        assertEquals(1, datatableEntryResponseNoGenericResult.size());

        Map<String, Object> responseMap = datatableEntryResponseNoGenericResult.get(0);
        assertEquals(clientID, responseMap.get("client_id"));
        assertEquals(datatableEntryMap.get(itsABoolean), Boolean.valueOf((String) responseMap.get(itsABoolean)));
        assertEquals(datatableEntryMap.get(itsADate), Utils.arrayDateToString((List) responseMap.get(itsADate)));
        assertDecimalEquals(datatableEntryMap.get(itsADecimal), responseMap.get(itsADecimal));
        assertEquals(datatableEntryMap.get(itsADatetime), Utils.arrayDateTimeToString((List<Integer>) responseMap.get(itsADatetime)));
        assertEquals(datatableEntryMap.get(tst_tst_tst_cd_itsADropdown), responseMap.get(tst_tst_tst_cd_itsADropdown));
        assertEquals(datatableEntryMap.get(itsANumber), responseMap.get(itsANumber));
        assertEquals(datatableEntryMap.get(itsAString), responseMap.get(itsAString));
        assertEquals(datatableEntryMap.get(itsAText), responseMap.get(itsAText));
        assertEquals(datatableEntryMap.get(itsAJson), responseMap.get(itsAJson));

        // Update datatable entry
        Boolean previousBoolean = (Boolean) datatableEntryMap.get(itsABoolean);
        datatableEntryMap.put(itsABoolean, !previousBoolean);
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADatetime, Utils.randomDateTimeGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(tst_tst_tst_cd_itsADropdown, null);
        datatableEntryMap.put(itsANumber, Utils.randomNumberGenerator(5));
        datatableEntryMap.put(itsAString, Utils.randomStringGenerator("", 8));
        datatableEntryMap.put(itsAText, Utils.randomStringGenerator("", 1000));

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PutDataTablesAppTableIdResponse updatedDatatableEntryResponse = datatableHelper.updateDatatableEntry(datatableName,
                clientID.longValue(), datatabelEntryRequestJsonString);

        assertEquals(clientID.longValue(), updatedDatatableEntryResponse.getClientId());

        Map<String, Object> changes = updatedDatatableEntryResponse.getChanges();
        assertEquals(datatableEntryMap.get(itsABoolean), changes.get(itsABoolean));
        assertEquals(datatableEntryMap.get(itsADate), Utils.arrayDateToString((List) changes.get(itsADate)));
        assertDecimalEquals(datatableEntryMap.get(itsADecimal), changes.get(itsADecimal));
        assertEquals(datatableEntryMap.get(itsADatetime), Utils.arrayDateTimeToString((List<Integer>) changes.get(itsADatetime)));
        assertEquals(datatableEntryMap.get(tst_tst_tst_cd_itsADropdown), changes.get(tst_tst_tst_cd_itsADropdown));
        assertEquals(datatableEntryMap.get(itsANumber), changes.get(itsANumber));
        assertEquals(datatableEntryMap.get(itsAString), changes.get(itsAString));
        assertEquals(datatableEntryMap.get(itsAText), changes.get(itsAText));

        List<String> columnsToValidate = List.of(itsABoolean, itsADate, itsADatetime, itsAString, itsAText, itsADecimal,
                tst_tst_tst_cd_itsADropdown);
        for (String column : columnsToValidate) {
            String valueFilter = column.equals(tst_tst_tst_cd_itsADropdown) ? createdCodeValueId.toString()
                    : datatableEntryMap.get(column).toString();
            String rows = Calls.ok(fineractClient().dataTables.queryValues(datatableName, column, valueFilter, column));
            JsonArray jsonArray = JsonParser.parseString(rows).getAsJsonArray();
            if (itsADatetime.equals(column)) {
                DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date parsedRequest = df1.parse(datatableEntryMap.get(column).toString());
                Date parsedResponse = df2.parse(jsonArray.get(0).getAsJsonObject().get(column).getAsString());
                assertFalse(parsedRequest.after(parsedResponse));
                assertFalse(parsedRequest.before(parsedResponse));
            } else if (itsADecimal.equals(column)) {
                assertEquals(0, new BigDecimal(datatableEntryMap.get(column).toString())
                        .compareTo(new BigDecimal(jsonArray.get(0).getAsJsonObject().get(column).getAsString())));
            } else if (tst_tst_tst_cd_itsADropdown.equals(column)) {
                assertEquals(createdCodeValueId.toString(), jsonArray.get(0).getAsJsonObject().get(column).getAsString());
            } else {
                assertEquals(datatableEntryMap.get(column).toString(), jsonArray.get(0).getAsJsonObject().get(column).getAsString());
            }
        }

        // deleting datatable entries
        Long appTableId = datatableHelper.deleteDatatableEntries(datatableName, clientID.longValue()).getResourceId();
        assertEquals(clientID.longValue(), appTableId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = datatableHelper.deleteDatatable(datatableName).getResourceIdentifier();
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");

        CallFailedRuntimeException notFound = assertThrows(CallFailedRuntimeException.class,
                () -> datatableHelper.getDatatable(datatableName));
        assertEquals(404, notFound.getStatus());
    }

    @Test
    public void validateCreateReadDeleteDatatableWithCaseSensitive() throws ParseException {
        // creating datatable for client entity
        String itsADate = "itsADate";
        String itsADecimal = "itsADecimal";
        String itsAString = "itsAString";
        String dateFormat = "dateFormat";

        PostDataTablesRequest datatableRequest = new PostDataTablesRequest()
                .datatableName(Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5)).apptableName(CLIENT_APP_TABLE_NAME)
                .entitySubType(PERSON_ENTITY_SUB_TYPE).multiRow(false).columns(
                        List.of(column(itsADate, "Date", true), column(itsADecimal, "Decimal", true), stringColumn(itsAString, 10L, true)));
        LOG.info("request : {}", datatableRequest);

        String datatableName = datatableHelper.createDatatable(datatableRequest).getResourceIdentifier();
        verifyDatatableCreatedOnServer(datatableName);

        // creating client with datatables
        final Integer clientID = ClientHelper.createClientAsPerson(requestSpec, responseSpec);

        // creating new client datatable entry
        final boolean genericResultSet = true;

        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(itsAString, Utils.randomStringGenerator("", 8));
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PostDataTablesAppTableIdResponse datatableEntryResponse = datatableHelper.createDatatableEntry(datatableName, clientID.longValue(),
                datatabelEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        final Map<String, Object> items = asMap(datatableHelper.getDatatableEntry(datatableName, clientID.longValue(),
                datatableEntryResponse.getResourceId(), genericResultSet, null));
        assertNotNull(items);
        assertEquals(1, ((List) items.get("data")).size());

        assertEquals("client_id", ((Map) ((List) items.get("columnHeaders")).get(0)).get("columnName"));
        assertEquals(clientID, ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(0));

        assertEquals(itsADate, ((Map) ((List) items.get("columnHeaders")).get(1)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADate),
                Utils.arrayDateToString((List) ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(1)));

        assertEquals(itsADecimal, ((Map) ((List) items.get("columnHeaders")).get(2)).get("columnName"));
        assertDecimalEquals(datatableEntryMap.get(itsADecimal), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(2));

        assertEquals(itsAString, ((Map) ((List) items.get("columnHeaders")).get(3)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsAString), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(3));

        // Update datatable entry
        final String randomValue = Utils.randomStringGenerator("", 8);
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(itsAString, randomValue);

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PutDataTablesAppTableIdResponse updatedDatatableEntryResponse = datatableHelper.updateDatatableEntry(datatableName,
                clientID.longValue(), datatabelEntryRequestJsonString);

        assertEquals(clientID.longValue(), updatedDatatableEntryResponse.getClientId());

        Map<String, Object> changes = updatedDatatableEntryResponse.getChanges();
        assertEquals(datatableEntryMap.get(itsADate), Utils.arrayDateToString((List) changes.get(itsADate)));
        assertDecimalEquals(datatableEntryMap.get(itsADecimal), changes.get(itsADecimal));
        assertEquals(datatableEntryMap.get(itsAString), changes.get(itsAString));

        // Read the datatable with a query
        LOG.info("query in {} for value : {}", itsAString, randomValue);
        final JsonNode queryResult = datatableHelper.queryValues(datatableName, itsAString, randomValue, "client_id,itsADecimal");
        assertNotNull(queryResult);
        LOG.info("query result : {}", queryResult);

        // deleting datatable entries
        Long appTableId = datatableHelper.deleteDatatableEntries(datatableName, clientID.longValue()).getResourceId();
        assertEquals(clientID.longValue(), appTableId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = datatableHelper.deleteDatatable(datatableName).getResourceIdentifier();
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");
    }

    @Test
    public void validateInsertNullValues() {
        // Fetch / Create TST code
        HashMap<String, Object> codeResponse = CodeHelper.getCodeByName(this.requestSpec, this.responseSpec, "TST_TST_TST");

        // creating datatable for client entity
        PostDataTablesRequest datatableRequest = new PostDataTablesRequest()
                .datatableName(Utils.uniqueRandomStringGenerator(LOAN_APP_TABLE_NAME + "_", 5)).apptableName(LOAN_APP_TABLE_NAME)
                .entitySubType("").multiRow(true)
                .columns(List.of(column("itsABoolean", "Boolean", false), column("itsADate", "Date", false),
                        column("itsADatetime", "Datetime", false), column("itsADecimal", "Decimal", false),
                        dropdownColumn("itsADropdown", "TST_TST_TST", false), column("itsANumber", "Number", false),
                        stringColumn("itsAString", 10L, false), column("itsAText", "Text", false)));
        LOG.info("request : {}", datatableRequest);

        String datatableName = datatableHelper.createDatatable(datatableRequest).getResourceIdentifier();
        verifyDatatableCreatedOnServer(datatableName);

        // try to create with the same name
        JsonNode response = assertFails(400, () -> datatableHelper.createDatatable(datatableRequest));
        assertEquals(VALIDATION_ERRORS_EXIST, response.get("userMessageGlobalisationCode").asText());

        // creating client with datatables
        final Integer clientID = ClientHelper.createClientAsPerson(requestSpec, responseSpec);
        final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingEnabled();
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        // creating new client datatable entry
        HashMap<String, Object> firstEntryMap = new HashMap<>();
        firstEntryMap.put("itsABoolean", null);
        firstEntryMap.put("itsADate", null);
        firstEntryMap.put("itsADatetime", null);
        firstEntryMap.put("itsADecimal", null);
        firstEntryMap.put("TST_TST_TST_cd_itsADropdown", null);
        firstEntryMap.put("itsANumber", null);
        firstEntryMap.put("itsAString", null);
        firstEntryMap.put("itsAText", null);

        firstEntryMap.put("locale", "en");
        firstEntryMap.put("dateFormat", "yyyy-MM-dd");

        String firstEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(firstEntryMap);
        LOG.info("map : {}", firstEntryRequestJsonString);

        PostDataTablesAppTableIdResponse firstEntryResponse = datatableHelper.createDatatableEntry(datatableName, loanID.longValue(),
                firstEntryRequestJsonString);
        assertNotNull(firstEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        HashMap<String, Object> secondEntryMap = new HashMap<>();
        secondEntryMap.put("itsABoolean", "");
        secondEntryMap.put("itsADate", "");
        secondEntryMap.put("itsADatetime", "");
        secondEntryMap.put("itsADecimal", "");
        secondEntryMap.put("TST_TST_TST_cd_itsADropdown", "");
        secondEntryMap.put("itsANumber", "");
        secondEntryMap.put("itsAString", "");
        secondEntryMap.put("itsAText", "");

        secondEntryMap.put("locale", "en");
        secondEntryMap.put("dateFormat", "yyyy-MM-dd");

        String secondEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(secondEntryMap);
        PostDataTablesAppTableIdResponse secondEntryResponse = datatableHelper.createDatatableEntry(datatableName, loanID.longValue(),
                secondEntryRequestJsonString);
        assertNotNull(secondEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        Map<String, Object> items = asMap(datatableHelper.getDatatableEntries(datatableName, loanID.longValue()));
        assertNotNull(items);
        assertEquals(2, ((List) items.get("data")).size());

        List headers = (List) items.get("columnHeaders");
        List firstEntryValues = (List) ((Map) ((List) items.get("data")).get(0)).get("row");
        assertEquals("id", ((Map) headers.get(0)).get("columnName"));
        assertEquals(1, firstEntryValues.get(0));
        assertEquals("loan_id", ((Map) headers.get(1)).get("columnName"));
        assertEquals(loanID, firstEntryValues.get(1));
        assertEquals("itsABoolean", ((Map) headers.get(2)).get("columnName"));
        assertNull(firstEntryValues.get(2));
        assertEquals("itsADate", ((Map) headers.get(3)).get("columnName"));
        assertNull(firstEntryValues.get(3));
        assertEquals("itsADatetime", ((Map) headers.get(4)).get("columnName"));
        assertNull(firstEntryValues.get(4));
        assertEquals("itsADecimal", ((Map) headers.get(5)).get("columnName"));
        assertNull(firstEntryValues.get(5));
        assertEquals("TST_TST_TST_cd_itsADropdown", ((Map) headers.get(6)).get("columnName"));
        assertNull(firstEntryValues.get(6));
        assertEquals("itsANumber", ((Map) headers.get(7)).get("columnName"));
        assertNull(firstEntryValues.get(7));
        assertEquals("itsAString", ((Map) headers.get(8)).get("columnName"));
        assertNull(firstEntryValues.get(8));
        assertEquals("itsAText", ((Map) headers.get(9)).get("columnName"));
        assertNull(firstEntryValues.get(9));

        List secondEntryValues = (List) ((Map) ((List) items.get("data")).get(1)).get("row");
        assertEquals(2, secondEntryValues.get(0));
        assertEquals(loanID, secondEntryValues.get(1));
        assertNull(secondEntryValues.get(2));
        assertNull(secondEntryValues.get(3));
        assertNull(secondEntryValues.get(4));
        assertNull(secondEntryValues.get(5));
        assertNull(secondEntryValues.get(6));
        assertNull(secondEntryValues.get(7));
        assertNull(secondEntryValues.get(8));
        assertNull(secondEntryValues.get(9));

        PutDataTablesAppTableIdDatatableIdResponse updatedDatatableEntryResponse = datatableHelper.updateDatatableEntry(datatableName,
                loanID.longValue(), 1L, secondEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(0, updatedDatatableEntryResponse.getChanges().size());
    }

    @Test
    public void validateCreateAndEditDatatable() {
        // Creating client
        final Integer clientId = ClientHelper.createClientAsPerson(requestSpec, responseSpec);
        final Integer randomNumber = Utils.randomNumberGenerator(3);

        // Creating datatable for Client Person
        final String datatableName = Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5);

        PostDataTablesRequest datatableRequest = new PostDataTablesRequest().datatableName(datatableName)
                .apptableName(CLIENT_APP_TABLE_NAME).entitySubType(CLIENT_PERSON_SUBTYPE_NAME).multiRow(false)
                .columns(List.of(column("itsANumber", "Number", false), stringColumn("itsAString", 10L, false)));
        LOG.info("request : {}", datatableRequest);

        PostDataTablesResponse datatableCreateResponse = datatableHelper.createDatatable(datatableRequest);
        assertEquals(datatableName, datatableCreateResponse.getResourceIdentifier());
        verifyDatatableCreatedOnServer(datatableName);

        // Insert first values
        final String randomString = Utils.randomStringGenerator("Q", 8);
        HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put("itsANumber", randomNumber);
        datatableEntryMap.put("itsAString", randomString);

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        String datatableEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(datatableEntryMap);
        PostDataTablesAppTableIdResponse datatableEntryResponse = datatableHelper.createDatatableEntry(datatableName, clientId.longValue(),
                datatableEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        Map<String, Object> items = asMap(datatableHelper.getDatatableEntries(datatableName, clientId.longValue()));
        assertNotNull(items);
        List data = (List) items.get("data");
        assertEquals(1, data.size());
        List records = (List) ((Map) data.get(0)).get("row");
        LOG.info("Record created at {}", records.get(3));
        LOG.info("Record updated at {}", records.get(4));

        assertEquals(clientId, records.get(0));
        assertEquals(randomString, records.get(2));

        // Update DataTable
        PutDataTablesRequest datatableUpdateRequest = new PutDataTablesRequest().apptableName(CLIENT_APP_TABLE_NAME)
                .entitySubType(CLIENT_PERSON_SUBTYPE_NAME)
                .addColumns(List.of(new PutDataTablesRequestAddColumns().name("itsAText").type("Text").mandatory(false)));
        LOG.info("request to update : {}", datatableUpdateRequest);
        PutDataTablesResponse datatableUpdateResponse = datatableHelper.updateDatatable(datatableName, datatableUpdateRequest);
        assertNotNull(datatableUpdateResponse);
        assertEquals(datatableName, datatableUpdateResponse.getResourceIdentifier());

        // Update DataTable Entry after Update DataTable schema
        datatableEntryMap = new HashMap<>();
        final String textValue = Utils.randomStringGenerator(randomString, 120);
        datatableEntryMap.put("itsAText", textValue);
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        datatableEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(datatableEntryMap);
        LOG.info("map to update : {}", datatableEntryRequestJsonString);
        PutDataTablesAppTableIdResponse updatedDatatableEntryResponse = datatableHelper.updateDatatableEntry(datatableName,
                clientId.longValue(), datatableEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(1, updatedDatatableEntryResponse.getChanges().size());

        // Read the Datatable entry generated with genericResultSet in true (default)
        items = asMap(datatableHelper.getDatatableEntries(datatableName, clientId.longValue()));
        assertNotNull(items);
        data = (List) items.get("data");
        assertEquals(1, data.size());

        records = (List) ((Map) data.get(0)).get("row");
        LOG.info("Record created at {}", records.get(3));
        LOG.info("Record updated at {}", records.get(4));

        assertEquals(clientId, records.get(0));
        assertEquals(randomString, records.get(2));
        assertEquals(textValue, records.get(5));

        Long resourceId = datatableHelper.deleteDatatableEntries(datatableName, clientId.longValue()).getResourceId();
        assertEquals(clientId.longValue(), resourceId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // Update - update, delete DataTable columns
        datatableUpdateRequest = new PutDataTablesRequest().apptableName(CLIENT_APP_TABLE_NAME).entitySubType(CLIENT_PERSON_SUBTYPE_NAME)
                .dropColumns(List.of(new PutDataTablesRequestDropColumns().name("itsANumber")))
                .changeColumns(List.of(new PutDataTablesRequestChangeColumns().name("itsAString").mandatory(false).length(100L)));
        LOG.info("request to update : {}", datatableUpdateRequest);
        datatableUpdateResponse = datatableHelper.updateDatatable(datatableName, datatableUpdateRequest);
        assertNotNull(datatableUpdateResponse);
        assertEquals(datatableName, datatableUpdateResponse.getResourceIdentifier());

        GetDataTablesResponse dataTable = datatableHelper.getDatatable(datatableName);
        assertEquals(CLIENT_PERSON_SUBTYPE_NAME, dataTable.getEntitySubType());
        List<ResultsetColumnHeaderData> columnHeaders = dataTable.getColumnHeaderData();
        assertEquals(5, columnHeaders.size());
        ResultsetColumnHeaderData stringColumn = columnHeaders.get(1);
        assertEquals("itsAString", stringColumn.getColumnName());
        assertEquals(100, stringColumn.getColumnLength());
    }

    @Test
    public void validateReadDatatableMultirow() {
        // Fetch / Create TST code
        String tst_tst_tst = "tst_tst_tst";
        HashMap<String, Object> codeResponse = CodeHelper.getCodeByName(this.requestSpec, this.responseSpec, tst_tst_tst);

        Integer createdCodeId = (Integer) codeResponse.get("id");
        Integer createdCodeValueId;
        Integer createdCodeValueIdSecond;
        if (createdCodeId == null) {
            createdCodeId = (Integer) CodeHelper.createCode(this.requestSpec, this.responseSpec, tst_tst_tst, "resourceId");

            createdCodeValueId = CodeHelper.createCodeValue(this.requestSpec, this.responseSpec, createdCodeId,
                    Utils.randomStringGenerator("cv_", 8), 1);
            createdCodeValueIdSecond = CodeHelper.createCodeValue(this.requestSpec, this.responseSpec, createdCodeId,
                    Utils.randomStringGenerator("cv_", 8), 2);
        } else {
            List<HashMap<String, Object>> codeValuesForCode = CodeHelper.getCodeValuesForCode(this.requestSpec, this.responseSpec,
                    createdCodeId, "");
            createdCodeValueId = (Integer) codeValuesForCode.get(0).get("id");
            createdCodeValueIdSecond = (Integer) codeValuesForCode.get(1).get("id");
        }

        // creating datatable for client entity
        PostDataTablesRequest datatableRequest = new PostDataTablesRequest()
                .datatableName(Utils.uniqueRandomStringGenerator(LOAN_APP_TABLE_NAME + "_", 5)).apptableName(LOAN_APP_TABLE_NAME)
                .entitySubType("").multiRow(true)
                .columns(List.of(column("itsABoolean", "Boolean", false), column("itsADate", "Date", false),
                        column("itsADatetime", "Datetime", false), column("itsADecimal", "Decimal", false),
                        dropdownColumn("itsADropdown", tst_tst_tst, false), column("itsANumber", "Number", false),
                        stringColumn("itsAString", 10L, false), column("itsAText", "Text", false)));
        LOG.info("request : {}", datatableRequest);

        String datatableName = datatableHelper.createDatatable(datatableRequest).getResourceIdentifier();
        verifyDatatableCreatedOnServer(datatableName);

        // try to create with the same name
        JsonNode response = assertFails(400, () -> datatableHelper.createDatatable(datatableRequest));
        assertEquals(VALIDATION_ERRORS_EXIST, response.get("userMessageGlobalisationCode").asText());

        // creating client with datatables
        final Integer clientID = ClientHelper.createClientAsPerson(requestSpec, responseSpec);
        final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingEnabled();
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        // creating new client datatable entry
        final boolean genericResultSet = true;

        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put("itsABoolean", Utils.randomNumberGenerator(1) % 2 == 0);
        datatableEntryMap.put("itsADate", Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put("itsADatetime", Utils.randomDateTimeGenerator("yyyy-MM-dd"));
        datatableEntryMap.put("itsADecimal", Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(tst_tst_tst + "_cd_itsADropdown", createdCodeValueId);
        datatableEntryMap.put("itsANumber", Utils.randomNumberGenerator(5));
        datatableEntryMap.put("itsAString", Utils.randomStringGenerator("", 8));
        datatableEntryMap.put("itsAText", Utils.randomStringGenerator("", 1000));

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PostDataTablesAppTableIdResponse datatableEntryResponseFirst = datatableHelper.createDatatableEntry(datatableName,
                loanID.longValue(), datatabelEntryRequestJsonString);
        PostDataTablesAppTableIdResponse datatableEntryResponseSecond = datatableHelper.createDatatableEntry(datatableName,
                loanID.longValue(), datatabelEntryRequestJsonString);
        assertNotNull(datatableEntryResponseFirst.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");
        assertNotNull(datatableEntryResponseSecond.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        Map<String, Object> items = asMap(datatableHelper.getDatatableEntries(datatableName, loanID.longValue()));
        assertNotNull(items);
        assertEquals(2, ((List) items.get("data")).size());

        assertEquals("id", ((Map) ((List) items.get("columnHeaders")).get(0)).get("columnName"));
        assertEquals(1, ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(0));
        assertEquals("loan_id", ((Map) ((List) items.get("columnHeaders")).get(1)).get("columnName"));
        assertEquals(loanID, ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(1));
        assertEquals("itsABoolean", ((Map) ((List) items.get("columnHeaders")).get(2)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsABoolean"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(2));
        assertEquals("itsADate", ((Map) ((List) items.get("columnHeaders")).get(3)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsADate"),
                Utils.arrayDateToString((List) ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(3)));
        assertEquals("itsADatetime", ((Map) ((List) items.get("columnHeaders")).get(4)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsADatetime"),
                Utils.arrayDateTimeToString((List) ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(4)));
        assertEquals("itsADecimal", ((Map) ((List) items.get("columnHeaders")).get(5)).get("columnName"));
        assertDecimalEquals(datatableEntryMap.get("itsADecimal"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(5));
        assertEquals(tst_tst_tst + "_cd_itsADropdown", ((Map) ((List) items.get("columnHeaders")).get(6)).get("columnName"));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(6));
        assertEquals("itsANumber", ((Map) ((List) items.get("columnHeaders")).get(7)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsANumber"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(7));
        assertEquals("itsAString", ((Map) ((List) items.get("columnHeaders")).get(8)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsAString"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(8));
        assertEquals("itsAText", ((Map) ((List) items.get("columnHeaders")).get(9)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsAText"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(9));

        assertEquals(2, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(0));
        assertEquals(loanID, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(1));
        assertEquals(datatableEntryMap.get("itsABoolean"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(2));
        assertEquals(datatableEntryMap.get("itsADate"),
                Utils.arrayDateToString((List) ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(3)));
        assertEquals(datatableEntryMap.get("itsADatetime"),
                Utils.arrayDateTimeToString((List) ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(4)));
        assertDecimalEquals(datatableEntryMap.get("itsADecimal"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(5));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(6));
        assertEquals(datatableEntryMap.get("itsANumber"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(7));
        assertEquals(datatableEntryMap.get("itsAString"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(8));
        assertEquals(datatableEntryMap.get("itsAText"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(9));

        // Read the Datatable entry generated with genericResultSet in false
        List<Map<String, Object>> datatableEntryResponseNoGenericResult = asList(datatableHelper.getDatatableEntry(datatableName,
                loanID.longValue(), datatableEntryResponseFirst.getResourceId(), !genericResultSet, null));
        assertNotNull(datatableEntryResponseNoGenericResult, "ERROR IN GETTING THE DATE VALUE FROM DATATABLE RECORD");
        assertEquals(1, datatableEntryResponseNoGenericResult.size());

        assertEquals(loanID, datatableEntryResponseNoGenericResult.get(0).get("loan_id"));
        assertEquals(datatableEntryMap.get("itsABoolean"),
                Boolean.valueOf((String) datatableEntryResponseNoGenericResult.get(0).get("itsABoolean")));
        assertEquals(datatableEntryMap.get("itsADate"),
                Utils.arrayDateToString((List) datatableEntryResponseNoGenericResult.get(0).get("itsADate")));
        assertDecimalEquals(datatableEntryMap.get("itsADecimal"), datatableEntryResponseNoGenericResult.get(0).get("itsADecimal"));
        assertEquals(datatableEntryMap.get("itsADatetime"),
                Utils.arrayDateTimeToString((List<Integer>) datatableEntryResponseNoGenericResult.get(0).get("itsADatetime")));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                datatableEntryResponseNoGenericResult.get(0).get(tst_tst_tst + "_cd_itsADropdown"));
        assertEquals(datatableEntryMap.get("itsANumber"), datatableEntryResponseNoGenericResult.get(0).get("itsANumber"));
        assertEquals(datatableEntryMap.get("itsAString"), datatableEntryResponseNoGenericResult.get(0).get("itsAString"));
        assertEquals(datatableEntryMap.get("itsAText"), datatableEntryResponseNoGenericResult.get(0).get("itsAText"));

        // Update datatable entry

        Boolean previousBoolean = (Boolean) datatableEntryMap.get("itsABoolean");

        datatableEntryMap.put("itsABoolean", null);
        datatableEntryMap.put("itsADate", null);
        datatableEntryMap.put("itsADatetime", null);
        datatableEntryMap.put("itsADecimal", null);
        datatableEntryMap.put(tst_tst_tst + "_cd_itsADropdown", null);
        datatableEntryMap.put("itsANumber", null);
        datatableEntryMap.put("itsAString", null);
        datatableEntryMap.put("itsAText", null);

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        datatabelEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PutDataTablesAppTableIdDatatableIdResponse updatedDatatableEntryResponse = datatableHelper.updateDatatableEntry(datatableName,
                loanID.longValue(), 1L, datatabelEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(1L, updatedDatatableEntryResponse.getResourceId());
        updatedDatatableEntryResponse = datatableHelper.updateDatatableEntry(datatableName, loanID.longValue(), 2L,
                datatabelEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(2L, updatedDatatableEntryResponse.getResourceId());

        assertEquals(Long.valueOf(loanID), updatedDatatableEntryResponse.getLoanId());

        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsABoolean"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsADate"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsADecimal"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsADatetime"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get(tst_tst_tst + "_cd_itsADropdown"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsANumber"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsAString"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsAText"));

        items = asMap(datatableHelper.getDatatableEntries(datatableName, loanID.longValue()));
        assertNotNull(items);
        assertEquals(2, ((List) items.get("data")).size());

        assertEquals("loan_id", ((Map) ((List) items.get("columnHeaders")).get(1)).get("columnName"));
        assertEquals(loanID, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(1));
        assertEquals("itsABoolean", ((Map) ((List) items.get("columnHeaders")).get(2)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(2));
        assertEquals("itsADate", ((Map) ((List) items.get("columnHeaders")).get(3)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(3));
        assertEquals("itsADatetime", ((Map) ((List) items.get("columnHeaders")).get(4)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(4));
        assertEquals("itsADecimal", ((Map) ((List) items.get("columnHeaders")).get(5)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(5));
        assertEquals(tst_tst_tst + "_cd_itsADropdown", ((Map) ((List) items.get("columnHeaders")).get(6)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(6));
        assertEquals("itsANumber", ((Map) ((List) items.get("columnHeaders")).get(7)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(7));
        assertEquals("itsAString", ((Map) ((List) items.get("columnHeaders")).get(8)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(8));
        assertEquals("itsAText", ((Map) ((List) items.get("columnHeaders")).get(9)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(9));

        // Read the Datatable entry generated with genericResultSet in false
        datatableEntryResponseNoGenericResult = asList(datatableHelper.getDatatableEntry(datatableName, loanID.longValue(),
                datatableEntryResponseFirst.getResourceId(), !genericResultSet, null));
        assertNotNull(datatableEntryResponseNoGenericResult, "ERROR IN GETTING THE DATE VALUE FROM DATATABLE RECORD");
        assertEquals(1, datatableEntryResponseNoGenericResult.size());

        assertEquals(loanID, datatableEntryResponseNoGenericResult.get(0).get("loan_id"));
        assertEquals(datatableEntryMap.get("itsABoolean"), datatableEntryResponseNoGenericResult.get(0).get("itsABoolean"));
        assertEquals(datatableEntryMap.get("itsADate"), datatableEntryResponseNoGenericResult.get(0).get("itsADate"));
        assertEquals(datatableEntryMap.get("itsADecimal"), datatableEntryResponseNoGenericResult.get(0).get("itsADecimal"));
        assertEquals(datatableEntryMap.get("itsADatetime"), datatableEntryResponseNoGenericResult.get(0).get("itsADatetime"));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                datatableEntryResponseNoGenericResult.get(0).get(tst_tst_tst + "_cd_itsADropdown"));
        assertEquals(datatableEntryMap.get("itsANumber"), datatableEntryResponseNoGenericResult.get(0).get("itsANumber"));
        assertEquals(datatableEntryMap.get("itsAString"), datatableEntryResponseNoGenericResult.get(0).get("itsAString"));
        assertEquals(datatableEntryMap.get("itsAText"), datatableEntryResponseNoGenericResult.get(0).get("itsAText"));

        // deleting datatable entries
        Long appTableId = datatableHelper.deleteDatatableEntries(datatableName, loanID.longValue()).getResourceId();
        assertEquals(loanID.longValue(), appTableId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = datatableHelper.deleteDatatable(datatableName).getResourceIdentifier();
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(LP_PRINCIPAL.toString())
                .withLoanTermFrequency(LOAN_TERM_FREQUENCY).withLoanTermFrequencyAsMonths().withNumberOfRepayments(LP_REPAYMENTS)
                .withRepaymentEveryAfter(LP_REPAYMENT_PERIOD).withRepaymentFrequencyTypeAsMonths()
                .withInterestRatePerPeriod(LP_INTEREST_RATE).withInterestTypeAsFlatBalance().withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod().withExpectedDisbursementDate(EXPECTED_DISBURSAL_DATE)
                .withSubmittedOnDate(LOAN_APPLICATION_SUBMISSION_DATE).withLoanType(INDIVIDUAL_LOAN)
                .build(clientID.toString(), loanProductID.toString(), null);
        return this.loanTransactionHelper.getLoanId(loanApplicationJSON);
    }

    private Integer createLoanProductWithPeriodicAccrualAccountingEnabled() {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(LP_PRINCIPAL.toString()).withRepaymentTypeAsMonth()
                .withRepaymentAfterEvery(LP_REPAYMENT_PERIOD).withNumberOfRepayments(LP_REPAYMENTS).withRepaymentTypeAsMonth()
                .withinterestRatePerPeriod(LP_INTEREST_RATE).withInterestRateFrequencyTypeAsMonths()
                .withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsFlat().withAccountingRuleAsNone().withDaysInMonth("30")
                .withDaysInYear("365").build(null);
        return this.loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    @Test
    public void testDropNullColumnWithData() {
        // Create datatable for client entity with one column that will have data and one that will be NULL
        PostDataTablesRequest datatableRequest = new PostDataTablesRequest()
                .datatableName(Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5)).apptableName(CLIENT_APP_TABLE_NAME)
                .entitySubType(CLIENT_PERSON_SUBTYPE_NAME).multiRow(false)
                .columns(List.of(stringColumn("columnWithData", 50L, false), stringColumn("columnWithNull", 50L, false)));
        LOG.info("Creating datatable: {}", datatableRequest);

        String datatableName = datatableHelper.createDatatable(datatableRequest).getResourceIdentifier();
        assertNotNull(datatableName);
        verifyDatatableCreatedOnServer(datatableName);

        // Create a client
        final Integer clientId = ClientHelper.createClientAsPerson(requestSpec, responseSpec);

        // Create a datatable entry with data in one column and NULL in the other
        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put("columnWithData", "TestValue");
        // columnWithNull is intentionally not set, so it will be NULL
        datatableEntryMap.put("locale", "en");

        String datatableEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("Creating datatable entry: {}", datatableEntryRequestJsonString);

        PostDataTablesAppTableIdResponse datatableEntryResponse = datatableHelper.createDatatableEntry(datatableName, clientId.longValue(),
                datatableEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");
        assertEquals(clientId.longValue(), datatableEntryResponse.getResourceId());

        // Verify column count before drop
        GetDataTablesResponse dataTableBeforeDrop = datatableHelper.getDatatable(datatableName);
        List<ResultsetColumnHeaderData> columnHeadersBeforeDrop = dataTableBeforeDrop.getColumnHeaderData();
        // Should have 5 columns before drop: client_id, columnWithData, columnWithNull, created_at, updated_at
        // Note: Datatables automatically add audit columns (created_at, updated_at)
        assertEquals(5, columnHeadersBeforeDrop.size(), "Should have 5 columns before dropping columnWithNull");

        // Now try to drop the NULL column - this should succeed with the fix
        PutDataTablesRequest updateRequest = new PutDataTablesRequest().apptableName(CLIENT_APP_TABLE_NAME)
                .entitySubType(CLIENT_PERSON_SUBTYPE_NAME)
                .dropColumns(List.of(new PutDataTablesRequestDropColumns().name("columnWithNull")));
        LOG.info("Dropping NULL column: {}", updateRequest);

        PutDataTablesResponse updateResponse = datatableHelper.updateDatatable(datatableName, updateRequest);
        assertNotNull(updateResponse);
        assertEquals(datatableName, updateResponse.getResourceIdentifier());

        // Verify the column was dropped
        GetDataTablesResponse dataTable = datatableHelper.getDatatable(datatableName);
        List<ResultsetColumnHeaderData> columnHeaders = dataTable.getColumnHeaderData();
        // Should have 4 columns after drop: client_id, columnWithData, created_at, updated_at (columnWithNull should be
        // dropped)
        assertEquals(4, columnHeaders.size(), "Should have 4 columns after dropping columnWithNull");
        boolean hasColumnWithData = false;
        boolean hasColumnWithNull = false;
        for (ResultsetColumnHeaderData header : columnHeaders) {
            if ("columnWithData".equals(header.getColumnName())) {
                hasColumnWithData = true;
            }
            if ("columnWithNull".equals(header.getColumnName())) {
                hasColumnWithNull = true;
            }
        }
        assertTrue(hasColumnWithData, "columnWithData should still exist");
        assertFalse(hasColumnWithNull, "columnWithNull should have been dropped");

        // Clean up
        datatableHelper.deleteDatatableEntries(datatableName, clientId.longValue());
        datatableHelper.deleteDatatable(datatableName);
    }

    @Test
    public void validateOrderParameterOnDatatableEntryRead() {
        // given: a single-row client datatable with a column name that requires quoting
        String plainColumn = "plaincolumn";
        String spacedColumn = "Spaced Column";
        String datatableName = Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5).toLowerCase();

        PostDataTablesRequest datatableRequest = new PostDataTablesRequest().datatableName(datatableName)
                .apptableName(CLIENT_APP_TABLE_NAME).entitySubType(CLIENT_PERSON_SUBTYPE_NAME).multiRow(false)
                .columns(List.of(column(plainColumn, "Number", false), stringColumn(spacedColumn, 20L, false)));
        String assignedDatatableName = datatableHelper.createDatatable(datatableRequest).getResourceIdentifier();
        assertEquals(datatableName, assignedDatatableName);

        final Integer clientID = ClientHelper.createClientAsPerson(requestSpec, responseSpec);

        final HashMap<String, Object> entryMap = new HashMap<>();
        entryMap.put(plainColumn, Utils.randomNumberGenerator(3));
        entryMap.put(spacedColumn, Utils.randomStringGenerator("", 8));
        entryMap.put("locale", "en");
        datatableHelper.createDatatableEntry(datatableName, clientID.longValue(), new Gson().toJson(entryMap));

        // valid: bare column, no direction
        JsonNode result = datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true, plainColumn);
        assertNotNull(result);

        // valid: bare column with direction
        result = datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true, plainColumn + " DESC");
        assertNotNull(result);

        // valid: double-quoted column with a space
        result = datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true, "\"" + spacedColumn + "\"");
        assertNotNull(result);

        // valid: backtick-quoted column with a space, plus direction
        result = datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true, "`" + spacedColumn + "` ASC");
        assertNotNull(result);

        // invalid: unknown column -- expect 403
        assertFails(403, () -> datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true, "not_a_real_column"));

        // invalid: classic SQL injection payload -- expect 403
        assertFails(403, () -> datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true,
                plainColumn + "; DROP TABLE m_client;--"));

        // invalid: unquoted column name containing a space -- expect 403 (quoting is required)
        assertFails(403, () -> datatableHelper.getDatatableEntries(datatableName, clientID.longValue(), true, spacedColumn));

        // cleanup
        datatableHelper.deleteDatatableEntries(datatableName, clientID.longValue());
        datatableHelper.deleteDatatable(datatableName);
    }

    @Test
    public void validateOrderParameterOnDatatableManyEntryRead() {
        // given: a multi-row client datatable so we can obtain a datatableId to query against
        String plainColumn = "plaincolumn";
        String spacedColumn = "Spaced Column";
        String datatableName = Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5).toLowerCase();

        PostDataTablesRequest datatableRequest = new PostDataTablesRequest().datatableName(datatableName)
                .apptableName(CLIENT_APP_TABLE_NAME).entitySubType(CLIENT_PERSON_SUBTYPE_NAME).multiRow(true)
                .columns(List.of(column(plainColumn, "Number", false), stringColumn(spacedColumn, 20L, false)));
        assertEquals(datatableName, datatableHelper.createDatatable(datatableRequest).getResourceIdentifier());

        final Integer clientID = ClientHelper.createClientAsPerson(requestSpec, responseSpec);

        final HashMap<String, Object> entryMap = new HashMap<>();
        entryMap.put(plainColumn, Utils.randomNumberGenerator(3));
        entryMap.put(spacedColumn, Utils.randomStringGenerator("", 8));
        entryMap.put("locale", "en");
        PostDataTablesAppTableIdResponse entryResponse = datatableHelper.createDatatableEntry(datatableName, clientID.longValue(),
                new Gson().toJson(entryMap));
        Long datatableId = entryResponse.getResourceId();
        assertNotNull(datatableId);

        // valid: bare column, no direction
        JsonNode result = datatableHelper.getDatatableEntry(datatableName, clientID.longValue(), datatableId, true, plainColumn);
        assertNotNull(result);

        // valid: bare column with direction
        result = datatableHelper.getDatatableEntry(datatableName, clientID.longValue(), datatableId, true, plainColumn + " DESC");
        assertNotNull(result);

        // valid: double-quoted column with a space
        result = datatableHelper.getDatatableEntry(datatableName, clientID.longValue(), datatableId, true, "\"" + spacedColumn + "\"");
        assertNotNull(result);

        // invalid: unknown column -- expect 403
        assertFails(403,
                () -> datatableHelper.getDatatableEntry(datatableName, clientID.longValue(), datatableId, true, "not_a_real_column"));

        // invalid: classic SQL injection payload -- expect 403
        assertFails(403, () -> datatableHelper.getDatatableEntry(datatableName, clientID.longValue(), datatableId, true,
                plainColumn + "; DROP TABLE m_client;--"));

        // invalid: subquery-based injection (the exact class of payload from the security report) -- expect 403
        assertFails(403, () -> datatableHelper.getDatatableEntry(datatableName, clientID.longValue(), datatableId, true,
                "(SELECT 1 FROM pg_sleep(3))"));

        // cleanup
        datatableHelper.deleteDatatableEntries(datatableName, clientID.longValue());
        datatableHelper.deleteDatatable(datatableName);
    }

    private void verifyDatatableCreatedOnServer(String datatableName) {
        assertEquals(datatableName, datatableHelper.getDatatable(datatableName).getRegisteredTableName());
    }

    /** Asserts the call is rejected with the given status and returns the error body for a closer look. */
    private static JsonNode assertFails(int expectedStatus, Executable call) {
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class, call);
        assertEquals(expectedStatus, exception.getStatus());
        try {
            return ObjectMapperFactory.getShared().readTree(exception.getResponseBody());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse the error body " + exception.getResponseBody(), e);
        }
    }

    /** Jackson reads a decimal column as {@code Double}; the test data is {@code Float}, so compare by value. */
    private static void assertDecimalEquals(Object expected, Object actual) {
        assertEquals(0, new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())),
                () -> "expected " + expected + " but was " + actual);
    }

    private static Map<String, Object> asMap(JsonNode node) {
        return ObjectMapperFactory.getShared().convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private static List<Map<String, Object>> asList(JsonNode node) {
        return ObjectMapperFactory.getShared().convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
    }
}
