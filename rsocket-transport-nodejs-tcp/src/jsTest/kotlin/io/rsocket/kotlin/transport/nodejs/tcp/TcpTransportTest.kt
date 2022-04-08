package io.rsocket.kotlin.transport.nodejs.tcp

import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.*

class TcpTransportTest : TransportTest() {
    private lateinit var server: TcpServer

    override suspend fun before() {
        val port = PortProvider.next()
        server = startServer(TcpServerTransport(port, "127.0.0.1", InUseTrackingPool))
        client = connectClient(TcpClientTransport(port, "127.0.0.1", InUseTrackingPool, testContext))
    }

    override suspend fun after() {
        delay(100) //TODO close race
        super.after()
        server.close()
    }
}
