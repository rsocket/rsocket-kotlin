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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.samples.chat.api.*
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
class ApiClient(rSocket: RSocket) {
    private val proto = ConfiguredProtoBuf
    val users = UserApiClient(rSocket, proto)
    val chats = ChatApiClient(rSocket, proto)
    val messages = MessageApiClient(rSocket, proto)
}

suspend fun ApiClient(
    address: ServerAddress,
    name: String
): ApiClient {
    println("Connecting client to: $address")
    val connector = RSocketConnector {
        connectionConfig {
            setupPayload { buildPayload { data(name) } }
        }
    }
    return ApiClient(connector.connect(ClientTransport(address)))
}
