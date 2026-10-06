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
import org.apache.fineract.client.models.CommandProcessingResult;

/**
 * {@code POST /makercheckers/{auditId}?command=approve} replays the queued command and answers with that command's full
 * {@code CommandProcessingResult} (for example {@code clientId} for an approved client creation, or {@code resourceId}
 * for an approved savings withdrawal). The spec documents the endpoint with {@code PostMakerCheckersResponse}, which
 * only carries {@code auditId}, so the generated {@code MakerCheckerOr4EyeFunctionalityApi#approveMakerCheckerEntry}
 * drops the fields a checker test asserts on. This interface binds the same call to the generated
 * {@code CommandProcessingResult} model instead.
 */
@Headers({ "Accept: application/json", "Content-Type: application/json" })
public interface MakerCheckerCommandsApi {

    @RequestLine("POST /v1/makercheckers/{auditId}?command={command}")
    CommandProcessingResult checkEntry(@Param("auditId") Long auditId, @Param("command") String command);
}
