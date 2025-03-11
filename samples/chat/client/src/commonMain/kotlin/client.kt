/*
 * Copyright 2015-2025 the original author or authors.
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

import io.ktor.client.engine.cio.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.samples.chat.api.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import io.rsocket.kotlin.transport.ktor.websocket.client.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.coroutines.*

@OptIn(ExperimentalSerializationApi::class)
class ApiClient(rSocket: RSocket) {
    private val proto = ConfiguredProtoBuf
    val users = UserApiClient(rSocket, proto)
    val chats = ChatApiClient(rSocket, proto)
    val messages = MessageApiClient(rSocket, proto)
}

suspend fun runClient(
    addresses: List<ServerAddress>,
    name: String,
    target: String,
): Unit = supervisorScope {
    addresses.forEach { address ->
        launch {
            val client = ApiClient(coroutineContext, address, name)
            val message = "RSocket is awesome! (from $target)"

            val chat = client.chats.all().firstOrNull() ?: client.chats.new("rsocket-kotlin chat")

            val sentMessage = client.messages.send(chat.id, message)
            println("Send to [$address]: $sentMessage")

            client.messages.messages(chat.id, -1).collect {
                println("Received from [$address]: $it")
            }
        }
    }
}

private suspend fun ApiClient(
    context: CoroutineContext,
    address: ServerAddress,
    name: String,
): ApiClient {
    println("Connecting client to: $address")
    val connector = RSocketConnector {
        connectionConfig {
            setupPayload { buildPayload { data(name) } }
        }
    }

    val target = when (address.type) {
        TransportType.TCP -> KtorTcpClientTransport(context).target(host = "127.0.0.1", port = address.port)
        TransportType.WS  -> KtorWebSocketClientTransport(context) {
            httpEngine(CIO)
        }.target(host = "127.0.0.1", port = address.port)
    }


    return ApiClient(connector.connect(target))
}
