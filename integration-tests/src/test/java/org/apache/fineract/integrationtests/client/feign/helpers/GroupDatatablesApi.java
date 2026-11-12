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

import feign.Headers;
import feign.RequestLine;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.PostGroupsResponse;

/**
 * Feign interface for creating a group together with its datatable entries. The server reads {@code datatables} on
 * {@code POST /groups} the same way it does on {@code POST /clients}, but {@code GroupsApiResourceSwagger} does not
 * declare the field, so the generated {@code PostGroupsRequest} cannot carry it.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface GroupDatatablesApi {

    @RequestLine("POST /v1/groups")
    PostGroupsResponse createGroupWithDatatables(GroupWithDatatablesRequest request);

    /** Request body for {@link #createGroupWithDatatables}: the pending-group fields plus {@code datatables}. */
    class GroupWithDatatablesRequest {

        private Long officeId;
        private String name;
        private String externalId;
        private Boolean active;
        private String submittedOnDate;
        private String dateFormat;
        private String locale;
        private List<DatatableEntry> datatables;

        public Long getOfficeId() {
            return officeId;
        }

        public GroupWithDatatablesRequest officeId(Long officeId) {
            this.officeId = officeId;
            return this;
        }

        public String getName() {
            return name;
        }

        public GroupWithDatatablesRequest name(String name) {
            this.name = name;
            return this;
        }

        public String getExternalId() {
            return externalId;
        }

        public GroupWithDatatablesRequest externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public Boolean getActive() {
            return active;
        }

        public GroupWithDatatablesRequest active(Boolean active) {
            this.active = active;
            return this;
        }

        public String getSubmittedOnDate() {
            return submittedOnDate;
        }

        public GroupWithDatatablesRequest submittedOnDate(String submittedOnDate) {
            this.submittedOnDate = submittedOnDate;
            return this;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public GroupWithDatatablesRequest dateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        public String getLocale() {
            return locale;
        }

        public GroupWithDatatablesRequest locale(String locale) {
            this.locale = locale;
            return this;
        }

        public List<DatatableEntry> getDatatables() {
            return datatables;
        }

        public GroupWithDatatablesRequest datatables(List<DatatableEntry> datatables) {
            this.datatables = datatables;
            return this;
        }
    }

    /** One datatable row to insert alongside the group. {@code data} holds the column values keyed by column name. */
    class DatatableEntry {

        private String registeredTableName;
        private Map<String, Object> data;

        public String getRegisteredTableName() {
            return registeredTableName;
        }

        public DatatableEntry registeredTableName(String registeredTableName) {
            this.registeredTableName = registeredTableName;
            return this;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public DatatableEntry data(Map<String, Object> data) {
            this.data = data;
            return this;
        }
    }
}
