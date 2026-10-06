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
 * Loan bulk-import endpoints whose bodies are Excel workbooks. The generated {@code LoansApi} and {@code BulkImportApi}
 * declare these as {@code void} because the OpenAPI spec cannot express the binary body, so the raw {@link Response} is
 * exposed here instead. Feign hands back a {@link Response} for any status, so callers must check
 * {@link Response#status()}.
 */
public interface LoanBulkImportApi {

    @RequestLine("GET /v1/loans/downloadtemplate?dateFormat={dateFormat}")
    @Headers("Accept: application/vnd.ms-excel")
    Response downloadLoanTemplate(@Param("dateFormat") String dateFormat);

    /**
     * The server reads {@code file}, {@code locale} and {@code dateFormat} as form-data parts; the generated client
     * declares them as {@code @Param} values, which the multipart encoder cannot combine with the file, so the parts
     * are assembled by the caller. Returns the import document id as plain text.
     */
    @RequestLine("POST /v1/loans/uploadtemplate")
    @Headers({ "Content-Type: multipart/form-data", "Accept: */*" })
    String uploadLoanTemplate(MultipartData multipartData);

    @RequestLine("GET /v1/imports/downloadOutputTemplate?importDocumentId={importDocumentId}")
    @Headers("Accept: application/vnd.ms-excel")
    Response downloadOutputTemplate(@Param("importDocumentId") String importDocumentId);
}
