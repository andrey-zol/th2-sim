/******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.th2.simulator.rule.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.NullValue;
import com.exactpro.evolution.api.phase_1.Value;
import com.exactpro.evolution.api.phase_1.Value.KindCase;

public abstract class MessageCompareRule extends AbstractRule {
    public static final String MESSAGE_NAME = "#MessageName";

    public MessageCompareRule(int id, @Nullable Map<String, String> arguments) {
        super(id, arguments);
    }

    @Override
    public boolean checkTriggered(@NotNull Message message) {
        if (getArguments().get(MESSAGE_NAME).equals(message.getMetadata().getMessageType())) {
            return getArguments().entrySet().stream().allMatch(entry -> {
                if (entry.getKey().startsWith("#")) {
                    return true;
                } else {
                    Value fieldValue = message.getFieldsOrDefault(entry.getKey(), Value
                            .newBuilder()
                            .setNullValue(NullValue.NULL_VALUE)
                            .build());
                    if (fieldValue.getKindCase() == KindCase.SIMPLE_VALUE) {
                        return fieldValue.getSimpleValue().equals(entry.getValue());
                    }
                }
                return false;
            });
        }
        return false;
    }

    @Override
    public @NotNull List<Message> handle(@NotNull Message message) {
        if (checkTriggered(message)) {
            return handleTriggered(message);
        } else {
            return Collections.emptyList();
        }
    }

    public abstract @NotNull List<Message> handleTriggered(@NotNull Message message);
}
