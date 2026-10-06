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
import feign.Param;
import feign.RequestLine;
import feign.Response;
import org.apache.fineract.client.feign.FineractMultipartEncoder.MultipartData;

/**
 * Bulk-import endpoints for clients, declared by hand because the generated {@code ClientApi} and {@code BulkImportApi}
 * cannot carry them: the two spreadsheet downloads are declared {@code void} (the OpenAPI spec has no binary schema for
 * them, see FINERACT-1227), and the upload is generated as a {@code File} parameter that the multipart encoder cannot
 * pair with the {@code locale} and {@code dateFormat} form fields.
 */
public interface ClientBulkImportApi {

    @RequestLine("GET /v1/clients/downloadtemplate?legalFormType={legalFormType}&dateFormat={dateFormat}")
    @Headers("Accept: application/vnd.ms-excel")
    Response downloadClientTemplate(@Param("legalFormType") String legalFormType, @Param("dateFormat") String dateFormat);

    /** Returns the import document id the server answers the upload with. */
    @RequestLine("POST /v1/clients/uploadtemplate?legalFormType={legalFormType}")
    @Headers("Accept: */*")
    Long uploadClientTemplate(@Param("legalFormType") String legalFormType, MultipartData multipartData);

    @RequestLine("GET /v1/imports/downloadOutputTemplate?importDocumentId={importDocumentId}")
    @Headers("Accept: application/vnd.ms-excel")
    Response downloadOutputTemplate(@Param("importDocumentId") Long importDocumentId);
}
