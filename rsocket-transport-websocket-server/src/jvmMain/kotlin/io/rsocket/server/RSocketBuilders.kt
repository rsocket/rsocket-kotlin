package io.rsocket.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.websocket.*
import io.rsocket.*
import io.rsocket.connection.*
import kotlinx.coroutines.*

fun Route.rSocket(path: String, protocol: String? = null, acceptor: RSocketAcceptor) {
    route(path, HttpMethod.Get) {
        rSocket(protocol, acceptor)
    }
}

@OptIn(InternalCoroutinesApi::class)
fun Route.rSocket(protocol: String? = null, acceptor: RSocketAcceptor) {
    val feature = application.feature(RSocketServerSupport)
    webSocket(protocol) {
        val connection = KtorWebSocketConnection(this)
        val connectionProvider = ConnectionProvider(connection)
        val server = RSocketServer(connectionProvider, feature.configuration)
        server.start(acceptor).join()
    }
}
