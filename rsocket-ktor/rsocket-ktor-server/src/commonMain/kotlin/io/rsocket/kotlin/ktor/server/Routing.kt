/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.ktor.server

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.*
import kotlinx.coroutines.*

public fun Route.rSocket(
    path: String? = null,
    protocol: String? = null,
    acceptor: ConnectionAcceptor,
): Unit = application.plugin(RSocketSupport).run {
    server.bindIn(application, KtorServerTransport(this@rSocket, path, protocol), acceptor)
}

private class KtorServerTransport(
    private val route: Route,
    private val path: String?,
    private val protocol: String?,
) : ServerTransport<Unit> {
    @TransportApi
    override fun CoroutineScope.start(accept: suspend CoroutineScope.(Connection) -> Unit) {
        val handler: suspend DefaultWebSocketServerSession.() -> Unit = {
            val connection = WebSocketConnection(this)
            accept(connection)
        }
        when (path) {
            null -> route.webSocket(protocol, handler)
            else -> route.webSocket(path, protocol, handler)
        }
    }
}
