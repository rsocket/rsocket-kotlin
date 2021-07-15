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

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.ktor.*
import io.rsocket.kotlin.transport.ktor.client.*
import kotlinx.coroutines.*

class Api(rSocket: RSocket) {
    private val proto = ConfiguredProtoBuf
    val users = UserApi(rSocket, proto)
    val chats = ChatApi(rSocket, proto)
    val messages = MessageApi(rSocket, proto)
}

suspend fun connectToApiUsingWS(name: String): Api {
    val client = HttpClient {
        install(WebSockets)
        install(RSocketSupport) {
            connector = connector(name)
        }
    }

    return Api(client.rSocket(port = 9000))
}

suspend fun connectToApiUsingTCP(name: String): Api {
    val transport = TcpClientTransport("0.0.0.0", 8000, CoroutineExceptionHandler { coroutineContext, throwable ->
        println("FAIL: $coroutineContext, $throwable")
    })
    return Api(connector(name).connect(transport))
}

private fun connector(name: String): RSocketConnector = RSocketConnector {
    connectionConfig {
        setupPayload { buildPayload { data(name) } }
    }
}
