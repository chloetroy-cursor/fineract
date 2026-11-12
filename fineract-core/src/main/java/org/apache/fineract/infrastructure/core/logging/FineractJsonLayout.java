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
package org.apache.fineract.infrastructure.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * The JSON log line written when {@code fineract.logging.json.enabled=true}.
 * <p>
 * One object per line with these fields, each left out when it has no value: {@code timestamp}, {@code level},
 * {@code thread}, {@code tenantId}, {@code correlationId}, {@code loanId}, {@code logger}, {@code message},
 * {@code mdc}, {@code context}, {@code exception}. The three identifiers are taken out of the MDC and written as fields
 * of their own, so a log search does not depend on how the MDC is nested; {@code mdc} holds whatever else is in the
 * MDC, and {@code exception} holds the stack trace.
 */
public class FineractJsonLayout extends JsonLayout {

    private static final List<String> IDENTIFIER_KEYS = List.of(LoggingContext.TENANT_ID, LoggingContext.CORRELATION_ID,
            LoggingContext.LOAN_ID);

    @Override
    protected Map<String, Object> toJsonMap(final ILoggingEvent event) {
        final Map<String, String> mdc = event.getMDCPropertyMap() == null ? Map.of() : event.getMDCPropertyMap();
        final Map<String, String> remainingMdc = new LinkedHashMap<>(mdc);
        remainingMdc.keySet().removeAll(IDENTIFIER_KEYS);

        final Map<String, Object> map = new LinkedHashMap<>();
        addTimestamp(TIMESTAMP_ATTR_NAME, this.includeTimestamp, event.getTimeStamp(), map);
        add(LEVEL_ATTR_NAME, this.includeLevel, String.valueOf(event.getLevel()), map);
        add(THREAD_ATTR_NAME, this.includeThreadName, event.getThreadName(), map);
        for (String key : IDENTIFIER_KEYS) {
            add(key, StringUtils.isNotBlank(mdc.get(key)), mdc.get(key), map);
        }
        add(LOGGER_ATTR_NAME, this.includeLoggerName, event.getLoggerName(), map);
        add(FORMATTED_MESSAGE_ATTR_NAME, this.includeFormattedMessage, event.getFormattedMessage(), map);
        add(MESSAGE_ATTR_NAME, this.includeMessage, event.getMessage(), map);
        addMap(MDC_ATTR_NAME, this.includeMDC, remainingMdc, map);
        add(CONTEXT_ATTR_NAME, this.includeContextName, event.getLoggerContextVO().getName(), map);
        addThrowableInfo(EXCEPTION_ATTR_NAME, this.includeException, event, map);
        addCustomDataToJsonMap(map, event);
        return map;
    }
}
