package io.rsocket.kotlin.transport.nodejs.tcp

import io.rsocket.kotlin.test.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.random.*

object PortProvider {
    private val port = atomic(Random.nextInt(20, 90) * 100)
    fun next(): Int = port.incrementAndGet()
}


class TcpTransportTest : TransportTest() {
    private val testJob = Job()

    private lateinit var server: TcpServer

    override suspend fun before() {
        val port = PortProvider.next()
        server = SERVER.bindIn(
            CoroutineScope(testJob + CoroutineExceptionHandler { c, e -> println("$c -> $e") }),
            TcpServerTransport(port, "127.0.0.1", InUseTrackingPool),
            ACCEPTOR
        )
        client = CONNECTOR.connect(TcpClientTransport(port, "127.0.0.1", InUseTrackingPool, testJob))
    }

    override suspend fun after() {
        delay(100) //TODO close race
        super.after()
        testJob.cancelAndJoin()
        server.close()
    }
}
