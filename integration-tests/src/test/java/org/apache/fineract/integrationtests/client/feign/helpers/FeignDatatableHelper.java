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

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import feign.template.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.models.DeleteDataTablesDatatableAppTableIdResponse;
import org.apache.fineract.client.models.DeleteDataTablesResponse;
import org.apache.fineract.client.models.GetDataTablesResponse;
import org.apache.fineract.client.models.PagedLocalRequestAdvancedQueryData;
import org.apache.fineract.client.models.PostDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PostDataTablesRequest;
import org.apache.fineract.client.models.PostDataTablesResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdDatatableIdResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PutDataTablesRequest;
import org.apache.fineract.client.models.PutDataTablesResponse;

public class FeignDatatableHelper {

    private static final String GENERIC_RESULT_SET = "genericResultSet";
    private static final String ORDER = "order";

    private final FineractFeignClient fineractClient;

    public FeignDatatableHelper(FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public PostDataTablesResponse createDatatable(PostDataTablesRequest request) {
        return ok(() -> fineractClient.dataTables().createDatatable(request));
    }

    public PutDataTablesResponse updateDatatable(String datatableName, PutDataTablesRequest request) {
        return ok(() -> fineractClient.dataTables().updateDatatable(datatableName, request));
    }

    public GetDataTablesResponse getDatatable(String datatableName) {
        return ok(() -> fineractClient.dataTables().getDatatable(datatableName));
    }

    /** Drops the datatable itself. The server rejects this while the datatable still holds entries. */
    public DeleteDataTablesResponse deleteDatatable(String datatableName) {
        return ok(() -> fineractClient.dataTables().deleteDatatable(datatableName));
    }

    public PostDataTablesAppTableIdResponse createDatatableEntry(String datatableName, Long apptableId, Map<String, Object> entry) {
        return createDatatableEntry(datatableName, apptableId, asJson(entry));
    }

    /**
     * Creates one entry in the given datatable for the given application-table row. The entry columns are datatable
     * specific, so the payload is passed as raw JSON.
     */
    public PostDataTablesAppTableIdResponse createDatatableEntry(String datatableName, Long apptableId, String entryJson) {
        return ok(() -> fineractClient.dataTables().createDatatableEntry(datatableName, apptableId, entryJson));
    }

    /** Updates the single entry of a one-to-one datatable. */
    public PutDataTablesAppTableIdResponse updateDatatableEntry(String datatableName, Long apptableId, String entryJson) {
        return ok(() -> fineractClient.dataTables().updateDatatableEntryOnetoOne(datatableName, apptableId, entryJson));
    }

    public PutDataTablesAppTableIdDatatableIdResponse updateDatatableEntry(String datatableName, Long apptableId, Long datatableId,
            Map<String, Object> entry) {
        return updateDatatableEntry(datatableName, apptableId, datatableId, asJson(entry));
    }

    public PutDataTablesAppTableIdDatatableIdResponse updateDatatableEntry(String datatableName, Long apptableId, Long datatableId,
            String entryJson) {
        return ok(() -> fineractClient.dataTables().updateDatatableEntryOneToMany(datatableName, apptableId, datatableId, entryJson));
    }

    /** Deletes every entry the given datatable holds for the given application-table row. */
    public DeleteDataTablesDatatableAppTableIdResponse deleteDatatableEntries(String datatableName, Long apptableId) {
        return ok(() -> fineractClient.dataTables().deleteDatatableEntries(datatableName, apptableId));
    }

    /**
     * A datatable's columns are defined at runtime, and the endpoint serves two different shapes depending on
     * {@code genericResultSet}, so the server declares its response as {@code String} on purpose. The entries are
     * therefore read as a tree rather than a generated model.
     */
    public JsonNode getDatatableEntries(String datatableName, Long apptableId) {
        return getDatatableEntries(datatableName, apptableId, true, null);
    }

    /**
     * Reads the entries the datatable holds for one application-table row. With {@code genericResultSet} the tree has
     * {@code columnHeaders} and {@code data[].row}; without it, it is an array of objects keyed by column name.
     * {@code order} is the raw order clause and may be null.
     */
    public JsonNode getDatatableEntries(String datatableName, Long apptableId, boolean genericResultSet, String order) {
        return readTree(datatableName,
                ok(() -> fineractClient.dataTables().getDatatableEntries(datatableName, apptableId, queryParams(genericResultSet, order))));
    }

    /** Reads one entry of a datatable by its own id, in the same two shapes as {@link #getDatatableEntries}. */
    public JsonNode getDatatableEntry(String datatableName, Long apptableId, Long datatableId, boolean genericResultSet, String order) {
        return readTree(datatableName, ok(() -> fineractClient.dataTables().getDatatableManyEntry(datatableName, apptableId, datatableId,
                queryParams(genericResultSet, order))));
    }

    /**
     * Rows of the datatable whose {@code columnFilter} column equals {@code valueFilter}, reduced to
     * {@code resultColumns}.
     */
    public JsonNode queryValues(String datatableName, String columnFilter, String valueFilter, String resultColumns) {
        return readTree(datatableName,
                ok(() -> fineractClient.dataTables().queryValues(datatableName, columnFilter, valueFilter, resultColumns)));
    }

    /** The page the server answers to an advanced query: {@code total} and {@code content[]} keyed by column name. */
    public JsonNode advancedQuery(String datatableName, PagedLocalRequestAdvancedQueryData request) {
        return readTree(datatableName, ok(() -> fineractClient.dataTables().advancedQuery(datatableName, request)));
    }

    /**
     * The generated query maps are declared {@code encoded=true}, so Feign sends their values verbatim. An order clause
     * such as {@code `Spaced Column` ASC} has to be percent-encoded here or the request URI is not even valid.
     */
    private static Map<String, Object> queryParams(boolean genericResultSet, String order) {
        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put(GENERIC_RESULT_SET, genericResultSet);
        if (order != null) {
            queryParams.put(ORDER, UriUtils.encode(order, StandardCharsets.UTF_8));
        }
        return queryParams;
    }

    private static JsonNode readTree(String datatableName, String json) {
        try {
            return ObjectMapperFactory.getShared().readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse the response of datatable " + datatableName, e);
        }
    }

    private String asJson(Map<String, Object> entry) {
        try {
            return ObjectMapperFactory.getShared().writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise the datatable entry " + entry, e);
        }
    }
}
