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
package org.apache.fineract.integrationtests.common;

import com.google.gson.Gson;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.portfolio.note.data.NoteData;

public final class NotesHelper {

    private static final Gson GSON = new JSON().getGson();

    private NotesHelper() {

    }

    private static final String SAVINGS_URL = "/fineract-provider/api/v1/savings";

    public static NoteData retrieveSavingsNote(RequestSpecification requestSpec, ResponseSpecification responseSpec, Integer savingsId,
            Integer noteId) {
        final String noteURL = SAVINGS_URL + "/" + savingsId + "/notes/" + noteId + "?" + Utils.TENANT_IDENTIFIER;
        final String response = Utils.performServerGet(requestSpec, responseSpec, noteURL);
        return GSON.fromJson(response, NoteData.class);
    }

}
