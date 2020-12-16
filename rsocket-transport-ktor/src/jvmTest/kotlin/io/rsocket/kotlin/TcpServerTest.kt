package io.rsocket.kotlin

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.*
import kotlinx.coroutines.*
import kotlin.test.*

class TcpServerTest : SuspendTest, TestWithLeakCheck {

    @Test
    fun testFailedConnection() = test {
        val tcp = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()

        val server = tcp.serverTransport()
        val job = RSocketServer().bind(server) {
            if (config.setupPayload.data.readText() == "ok") RSocketRequestHandler {} else error("FAILED")
        }

        suspend fun newClient(text: String) = RSocketConnector {
            connectionConfig {
                setupPayload {
                    payload(text)
                }
            }
        }.connect(tcp.clientTransport(server.socket.localAddress))

        val client1 = newClient("ok")

        val client2 = newClient("not ok")

        val client3 = newClient("ok")

        assertTrue(client1.isActive)
        assertFalse(client2.isActive)
        assertTrue(client3.isActive)

        assertTrue(job.isActive)
    }
}
