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

package io.rsocket.kotlin.ktor.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

private val RSocketSupportConfigKey = AttributeKey<RSocketSupportConfig.Internal>("RSocketSupportConfig")

public val RSocketSupport: ClientPlugin<RSocketSupportConfig> = createClientPlugin("RSocketSupport", ::RSocketSupportConfig) {
    client.pluginOrNull(WebSockets) ?: error("RSocket require WebSockets to work. You must install WebSockets plugin first.")
    client.attributes.put(RSocketSupportConfigKey, pluginConfig.toInternal())
}

public class RSocketSupportConfig internal constructor() {
    private var connector = RSocketConnector()

    public fun connector(connector: RSocketConnector) {
        this.connector = connector
    }

    public fun connector(block: RSocketConnectorBuilder.() -> Unit) {
        this.connector = RSocketConnector(block)
    }

    internal fun toInternal(): Internal = Internal(connector)
    internal class Internal(val connector: RSocketConnector)
}

internal suspend fun HttpClient.connectRSocket(request: HttpRequestBuilder.() -> Unit): RSocket {
    val config = attributes.getOrNull(RSocketSupportConfigKey)
        ?: error("Plugin `RSocketSupport` is not installed. Consider using `install(RSocketSupport)` in client config first.")

    return config.connector.connect(RSocketSupportTarget(this, request))
}

@OptIn(RSocketTransportApi::class)
private class RSocketSupportTarget(
    private val client: HttpClient,
    private val request: HttpRequestBuilder.() -> Unit,
) : RSocketClientTarget {
    override val coroutineContext: CoroutineContext get() = client.coroutineContext

    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
        client.webSocket(request) {
            handler.handleKtorWebSocketConnection(this)
        }
    }
}
