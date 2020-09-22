/*
 * Copyright 2015-2020 the original author or authors.
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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@OptIn(ExperimentalSerializationApi::class)
actual class ChatApi(private val rSocket: RSocket, private val proto: ProtoBuf) {
    actual suspend fun all(): List<Chat> = proto.decodeFromPayload(
        rSocket.requestResponse(Payload(route = "chats.all", ByteReadPacket.Empty))
    )

    actual suspend fun new(name: String): Chat = proto.decodeFromPayload(
        rSocket.requestResponse(
            proto.encodeToPayload(route = "chats.new", NewChat(name))
        )
    )

    actual suspend fun delete(id: Int) {
        rSocket.requestResponse(
            proto.encodeToPayload(route = "chats.delete", DeleteChat(id))
        ).release()
    }
}
