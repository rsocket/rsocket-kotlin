package io.rsocket

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.rsocket.client.*
import io.rsocket.connection.*
import io.rsocket.server.*
import kotlinx.coroutines.*

suspend fun Socket.rSocketClient(configuration: RSocketClientConfiguration = RSocketClientConfiguration()): RSocket {
    val connection = KtorTcpConnection(this)
    val connectionProvider = ConnectionProvider(connection)
    return RSocketClient(connectionProvider, configuration).connect()
}

suspend fun Socket.rSocketServer(configuration: RSocketServerConfiguration = RSocketServerConfiguration(), acceptor: RSocketAcceptor): Job {
    val connection = KtorTcpConnection(this)
    val connectionProvider = ConnectionProvider(connection)
    val server = RSocketServer(connectionProvider, configuration)
    return server.start(acceptor)
}

@OptIn(KtorExperimentalAPI::class)
suspend fun ServerSocket.rSocket(
    configuration: RSocketServerConfiguration = RSocketServerConfiguration(),
    acceptor: RSocketAcceptor
) {
    while (true) {
        val socket = accept()
        GlobalScope.launch(socket.socketContext) {
            val connection = KtorTcpConnection(socket)
            val connectionProvider = ConnectionProvider(connection)
            val server = RSocketServer(connectionProvider, configuration)
            server.start(acceptor).join()
        }
    }
}
