package io.rsocket

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.rsocket.client.*
import io.rsocket.server.*
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

@OptIn(KtorExperimentalAPI::class)
class WebSocketTransportTest : TransportTest() {
    override suspend fun init(): RSocket = httpClient.rSocket(port = 9000)

    companion object {

        private val httpClient = HttpClient(ClientCIO) {
            install(WebSockets)
            install(RSocketClientSupport)
        }

        init {
            embeddedServer(ServerCIO, port = 9000) {
                install(RSocketServerSupport)
                routing {
                    rSocket {
                        TestRSocket()
                    }
                }
            }.start()
        }
    }
}
