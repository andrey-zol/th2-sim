/*******************************************************************************
 *  Copyright 2020 Exactpro (Exactpro Systems Limited)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/

package com.exactpro.th2.simulator.util

import com.exactpro.th2.infra.grpc.ConnectionID
import com.exactpro.th2.infra.grpc.Direction
import com.exactpro.th2.infra.grpc.Message
import com.exactpro.th2.infra.grpc.MessageID
import com.exactpro.th2.infra.grpc.MessageMetadata
import com.exactpro.th2.infra.grpc.Value

fun Message.getField(key: String): Value? = this.getFieldsOrDefault(key, null)
fun Message.Builder.getField(key: String) : Value? = this.getFieldsOrDefault(key, null)

fun Message.Builder.addField(key: String, value: Any?) = this.putFields(key, value?.toValue() ?: ValueUtils.nullValue())
fun Message.Builder.addFields(fields: Map<String, Any?>?) = fields?.forEach {this.addField(it.key, it.value)}
fun Message.Builder.copyField(message: Message.Builder, vararg key: String) = key.forEach { this.putFields(it, message.getField(it) ?: ValueUtils.nullValue())}
fun Message.Builder.copyField(message: Message, vararg key: String) = key.forEach { this.putFields(it, message.getField(it) ?: ValueUtils.nullValue()) }
fun Message.copy() = Message.newBuilder().putAllFields(this.fieldsMap)
fun Message.Builder.copy() = Message.newBuilder().putAllFields(this.fieldsMap)
fun Message.Builder.setMetadata(messageType: String?, direction: Direction?, sessionAlias: String?) {
    this.setMetadata(MessageMetadata.newBuilder().also {
        if (messageType != null) {it.messageType = messageType}
        if (direction != null || sessionAlias != null) {
            it.id = MessageID.newBuilder().apply {
                if (direction != null) { this.direction = direction}
                if (sessionAlias != null) {this.connectionId = ConnectionID.newBuilder().setSessionAlias(sessionAlias).build() }
            }.build()
        }
    })
}

fun Message.Builder.setMetadata(messageType: String?, sessionAlias: String?) = setMetadata(messageType, null, sessionAlias)
fun Message.Builder.setMessageType(messageType: String?) = setMetadata(messageType, null, null)