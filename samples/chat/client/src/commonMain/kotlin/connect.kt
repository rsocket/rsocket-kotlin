package io.rsocket.kotlin.samples.chat.client

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.ktor.*
import io.rsocket.kotlin.transport.ktor.client.*

private fun connector(name: String): RSocketConnector = RSocketConnector {
    connectionConfig {
        setupPayload { buildPayload { data(name) } }
    }
}

//not supported on native
suspend fun connectToApiUsingWS(name: String, port: Int = 9000): ApiClient {
    val client = HttpClient {
        install(WebSockets)
        install(RSocketSupport) {
            connector = connector(name)
        }
    }

    return ApiClient(client.rSocket(port = port))
}

//not supported on JS
suspend fun connectToApiUsingTCP(name: String, port: Int = 8000): ApiClient {
    val transport = TcpClientTransport("0.0.0.0", port)
    return ApiClient(connector(name).connect(transport))
}
