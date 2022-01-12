package io.rsocket.kotlin.samples.chat.server

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.ktor.*
import kotlinx.coroutines.*

fun main() = runBlocking {
    val acceptor = acceptor()

    val rSocketServer = RSocketServer()

    //start TCP server
    rSocketServer.bindIn(
        CoroutineScope(CoroutineExceptionHandler { coroutineContext, throwable ->
            println("Error happened $coroutineContext: $throwable")
        }),
        TcpServerTransport("0.0.0.0", 7000),
        acceptor
    ).handlerJob.join()
}
