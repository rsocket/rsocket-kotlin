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

package io.rsocket.kotlin.ktor.server

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*

private val RSocketSupportConfigKey = AttributeKey<RSocketSupportConfig.Internal>("RSocketSupportConfig")

public val RSocketSupport: ApplicationPlugin<RSocketSupportConfig> = createApplicationPlugin("RSocketSupport", ::RSocketSupportConfig) {
    application.pluginOrNull(WebSockets) ?: error("RSocket require WebSockets to work. You must install WebSockets plugin first.")
    application.attributes.put(RSocketSupportConfigKey, pluginConfig.toInternal())
}

public class RSocketSupportConfig internal constructor() {
    private var server: RSocketServer = RSocketServer()

    public fun server(server: RSocketServer) {
        this.server = server
    }

    public fun server(block: RSocketServerBuilder.() -> Unit) {
        server = RSocketServer(block)
    }


    internal fun toInternal(): Internal = Internal(server)
    internal class Internal(val server: RSocketServer)
}


@OptIn(RSocketTransportApi::class)
internal fun Route.rSocketHandler(acceptor: ConnectionAcceptor): suspend DefaultWebSocketServerSession.() -> Unit {
    val config = application.attributes.getOrNull(RSocketSupportConfigKey)
        ?: error("Plugin RSocketSupport is not installed. Consider using `install(RSocketSupport)` in server config first.")

    return {
        config.server.acceptConnection(acceptor, KtorWebSocketConnection(this))
        awaitCancellation()
    }
}
