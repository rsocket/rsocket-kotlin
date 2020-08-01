package io.rsocket.client

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.rsocket.*
import io.rsocket.connection.*
import io.rsocket.flow.*
import io.rsocket.keepalive.*
import io.rsocket.payload.*
import io.rsocket.plugin.*

class RSocketClientSupport(
    private val configuration: RSocketClientConfiguration
) {

    class Config internal constructor() {
        var plugin: Plugin = Plugin()
        var fragmentation: Int = 0
        var keepAlive: KeepAlive = KeepAlive()
        var payloadMimeType: PayloadMimeType = PayloadMimeType()
        var setupPayload: Payload = Payload.Empty
        var requestStrategy: () -> RequestStrategy = RequestStrategy.Default

        var acceptor: RSocketAcceptor = { RSocketRequestHandler { } }

        internal fun build(): RSocketClientSupport = RSocketClientSupport(
            RSocketClientConfiguration(
                plugin = plugin,
                fragmentation = fragmentation,
                keepAlive = keepAlive,
                payloadMimeType = payloadMimeType,
                setupPayload = setupPayload,
                requestStrategy = requestStrategy,
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
                val connection = KtorWebSocketConnection(session)
                val transport = ConnectionProvider(connection)
                val client = RSocketClient(transport, feature.configuration)
                val rSocket = client.connect()
                val response = HttpResponseContainer(info, rSocket)
                proceedWith(response)
            }
        }
    }

}
