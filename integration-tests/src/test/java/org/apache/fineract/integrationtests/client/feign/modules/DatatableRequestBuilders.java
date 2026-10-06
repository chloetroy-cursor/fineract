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

import java.util.List;
import org.apache.fineract.client.models.PostColumnHeaderData;
import org.apache.fineract.client.models.PostDataTablesRequest;
import org.apache.fineract.integrationtests.common.Utils;

/** Request factories for the datatable API. */
public final class DatatableRequestBuilders {

    public static final String PERSON_ENTITY_SUB_TYPE = "PERSON";

    private DatatableRequestBuilders() {}

    /**
     * The family-details datatable the legacy tests register against an application table. Its column names are what
     * {@code ClientHelper}, {@code GroupHelper} and the savings and loan builders fill in when they create an entity
     * "with datatables", so they must not change. The datatable name is random and prefixed with the application table.
     */
    public static PostDataTablesRequest testDatatable(String apptableName, boolean multiRow) {
        return new PostDataTablesRequest().datatableName(Utils.uniqueRandomStringGenerator(apptableName + "_", 5))
                .apptableName(apptableName).entitySubType(PERSON_ENTITY_SUB_TYPE).multiRow(multiRow)
                .columns(List.of(stringColumn("Spouse Name", 25L, true), numberColumn("Number of Dependents"),
                        column("Time of Visit", "DateTime", false), column("Date of Approval", "Date", false)));
    }

    public static PostColumnHeaderData column(String name, String type, boolean mandatory) {
        return new PostColumnHeaderData().name(name).type(type).mandatory(mandatory);
    }

    public static PostColumnHeaderData stringColumn(String name, long length, boolean mandatory) {
        return column(name, "String", mandatory).length(length);
    }

    public static PostColumnHeaderData numberColumn(String name) {
        return column(name, "Number", true);
    }

    public static PostColumnHeaderData dropdownColumn(String name, String codeName, boolean mandatory) {
        return column(name, "Dropdown", mandatory).code(codeName);
    }
}
