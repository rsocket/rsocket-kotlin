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

package io.rsocket

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.client.*
import io.rsocket.connection.*
import io.rsocket.server.*
import kotlinx.coroutines.*

suspend fun Socket.rSocketClient(configuration: RSocketClientConfiguration = RSocketClientConfiguration()): RSocket {
    val connection = KtorTcpConnection(this)
    val connectionProvider = ConnectionProvider(connection)
    return RSocketClient(connectionProvider, configuration).connect()
}

suspend fun Socket.rSocketServer(configuration: RSocketServerConfiguration = RSocketServerConfiguration(), acceptor: RSocketAcceptor): Job {
    val connection = KtorTcpConnection(this)
    val connectionProvider = ConnectionProvider(connection)
    val server = RSocketServer(connectionProvider, configuration)
    return server.start(acceptor)
}

@OptIn(KtorExperimentalAPI::class)
suspend fun ServerSocket.rSocket(
    configuration: RSocketServerConfiguration = RSocketServerConfiguration(),
    acceptor: RSocketAcceptor
) {
    while (true) {
        val socket = accept()
        GlobalScope.launch(socket.socketContext) {
            val connection = KtorTcpConnection(socket)
            val connectionProvider = ConnectionProvider(connection)
            val server = RSocketServer(connectionProvider, configuration)
            server.start(acceptor).join()
        }
    }
}
