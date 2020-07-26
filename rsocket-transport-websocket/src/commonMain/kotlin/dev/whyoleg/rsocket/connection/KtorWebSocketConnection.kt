package dev.whyoleg.rsocket.connection

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

class KtorWebSocketConnection(private val session: WebSocketSession) : Connection {
    override val job: Job get() = session.coroutineContext[Job]!!

    override suspend fun send(bytes: ByteArray) {
//        println("SEND: $frame")
        session.send(bytes)
    }

    override suspend fun receive(): ByteArray {
        val frame = session.incoming.receive().data
//        println("RECEIVE: $frame")
        return frame
    }
}
