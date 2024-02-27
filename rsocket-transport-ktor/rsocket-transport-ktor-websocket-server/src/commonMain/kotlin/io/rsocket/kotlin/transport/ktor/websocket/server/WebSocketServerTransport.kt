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

package io.rsocket.kotlin.transport.ktor.websocket.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.*

//TODO: will be reworked later with transport API rework

@Suppress("FunctionName")
public fun <A : ApplicationEngine, T : ApplicationEngine.Configuration> WebSocketServerTransport(
    engineFactory: ApplicationEngineFactory<A, T>,
    port: Int = 80, host: String = "0.0.0.0",
    path: String? = null, protocol: String? = null,
    engine: T.() -> Unit = {},
    webSockets: WebSockets.WebSocketOptions.() -> Unit = {},
): ServerTransport<A> = WebSocketServerTransport(
    engineFactory,
    EngineConnectorBuilder().apply {
        this.port = port
        this.host = host
    } as EngineConnectorConfig,
    path = path,
    protocol = protocol,
    engine = engine,
    webSockets = webSockets,
)

@Suppress("FunctionName")
@OptIn(TransportApi::class)
public fun <A : ApplicationEngine, T : ApplicationEngine.Configuration> WebSocketServerTransport(
    engineFactory: ApplicationEngineFactory<A, T>,
    vararg connectors: EngineConnectorConfig,
    path: String? = null, protocol: String? = null,
    engine: T.() -> Unit = {},
    webSockets: WebSockets.WebSocketOptions.() -> Unit = {},
): ServerTransport<A> = ServerTransport { acceptor ->
    val handler: suspend DefaultWebSocketServerSession.() -> Unit = {
        val connection = WebSocketConnection(this)
        acceptor(connection)
    }
    embeddedServer(engineFactory, *connectors, configure = engine) {
        install(WebSockets, webSockets)
        routing {
            when (path) {
                null -> webSocket(protocol, handler)
                else -> webSocket(path, protocol, handler)
            }
        }
    }.also {
        it.start(wait = false)
    }
}
