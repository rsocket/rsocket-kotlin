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

package io.rsocket.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.websocket.*
import io.rsocket.*
import io.rsocket.connection.*
import kotlinx.coroutines.*

fun Route.rSocket(path: String, protocol: String? = null, acceptor: RSocketAcceptor) {
    route(path, HttpMethod.Get) {
        rSocket(protocol, acceptor)
    }
}

@OptIn(InternalCoroutinesApi::class)
fun Route.rSocket(protocol: String? = null, acceptor: RSocketAcceptor) {
    val feature = application.feature(RSocketServerSupport)
    webSocket(protocol) {
        val connection = KtorWebSocketConnection(this)
        val connectionProvider = ConnectionProvider(connection)
        val server = RSocketServer(connectionProvider, feature.configuration)
        server.start(acceptor).join()
    }
}
