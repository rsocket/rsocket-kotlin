package io.rsocket.kotlin.samples.chat.server

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.ktor.*
import io.rsocket.kotlin.transport.ktor.server.*

fun main() {
    val acceptor = acceptor()

    val rSocketServer = RSocketServer()

    //start TCP server
    rSocketServer.bind(TcpServerTransport("0.0.0.0", 8000), acceptor)

    //start WS server
    embeddedServer(CIO, port = 9000) {
        install(WebSockets)
        install(RSocketSupport) {
            server = rSocketServer
        }

        routing {
            rSocket(acceptor = acceptor)
        }
    }.start(true)
}
