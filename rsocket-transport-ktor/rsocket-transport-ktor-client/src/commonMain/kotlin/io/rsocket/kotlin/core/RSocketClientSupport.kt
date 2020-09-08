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

package io.rsocket.kotlin.core

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.plugin.*

class RSocketClientSupport(
    private val configuration: RSocketConnectorConfiguration,
) {

    class Config internal constructor() {
        var plugin: Plugin = Plugin()
        var fragmentation: Int = 0
        var keepAlive: KeepAlive = KeepAlive()
        var payloadMimeType: PayloadMimeType = PayloadMimeType()
        var setupPayload: Payload = Payload.Empty

        var acceptor: RSocketAcceptor = { RSocketRequestHandler { } }

        fun fromConfig(config: RSocketConnectorConfiguration) {
            plugin = config.plugin
            fragmentation = config.fragmentation
            keepAlive = config.keepAlive
            payloadMimeType = config.payloadMimeType
            setupPayload = config.setupPayload
            acceptor = config.acceptor
        }

        internal fun build(): RSocketClientSupport = RSocketClientSupport(
            RSocketConnectorConfiguration(
                plugin = plugin,
                fragmentation = fragmentation,
                keepAlive = keepAlive,
                payloadMimeType = payloadMimeType,
                setupPayload = setupPayload,
                acceptor = acceptor
            )
        )
    }

    companion object Feature : HttpClientFeature<Config, RSocketClientSupport> {
        override val key = AttributeKey<RSocketClientSupport>("RSocket")

        override fun prepare(block: Config.() -> Unit): RSocketClientSupport = Config().apply(block).build()

        override fun install(feature: RSocketClientSupport, scope: HttpClient) {
            scope.responsePipeline.intercept(HttpResponsePipeline.After) { (info, session) ->
                if (session !is WebSocketSession) return@intercept
                if (info.type != RSocket::class) return@intercept
                val rSocket = session.connection.connectClient(feature.configuration)
                val response = HttpResponseContainer(info, rSocket)
                proceedWith(response)
            }
        }
    }

}
