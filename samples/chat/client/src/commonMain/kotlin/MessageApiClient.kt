/*
 * Copyright 2015-2022 the original author or authors.
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
 */

package io.rsocket.kotlin.samples.chat.client

import io.rsocket.kotlin.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
class MessageApiClient(private val rSocket: RSocket, private val proto: ProtoBuf) : MessageApi {
    override suspend fun send(chatId: Int, content: String): Message = proto.decodeFromPayload(
        rSocket.requestResponse(
            proto.encodeToPayload(route = "messages.send", SendMessage(chatId, content))
        )
    )

    override suspend fun history(chatId: Int, limit: Int): List<Message> = proto.decodeFromPayload(
        rSocket.requestResponse(
            proto.encodeToPayload(route = "messages.history", HistoryMessages(chatId, limit))
        )
    )

    override fun messages(chatId: Int, fromMessageId: Int): Flow<Message> = rSocket.requestStream(
        proto.encodeToPayload(route = "messages.stream", StreamMessages(chatId, fromMessageId))
    ).map {
        proto.decodeFromPayload(it)
    }
}
