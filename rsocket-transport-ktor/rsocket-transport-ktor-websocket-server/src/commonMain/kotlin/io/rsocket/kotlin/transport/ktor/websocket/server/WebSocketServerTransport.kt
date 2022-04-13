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

package io.rsocket.kotlin.transport.ktor.websocket.server

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.*

@OptIn(TransportApi::class)
internal fun Route.serverTransport(
    path: String?,
    protocol: String?,
): ServerTransport<Unit> = ServerTransport { acceptor ->
    when (path) {
        null -> webSocket(protocol) {
            val connection = WebSocketConnection(this)
            acceptor(connection)
        }
        else -> webSocket(path, protocol) {
            val connection = WebSocketConnection(this)
            acceptor(connection)
        }
    }
}
