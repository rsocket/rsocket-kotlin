package ws

import dev.whyoleg.rsocket.server.*
import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import rSocketAcceptor

fun main() {
    embeddedServer(CIO) {
        install(RSocketServerSupport)
        routing {
            rSocket(acceptor = rSocketAcceptor)
        }
    }.start(true)
}
