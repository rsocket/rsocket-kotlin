package io.rsocket.kotlin

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.*
import kotlinx.coroutines.*
import kotlin.test.*

class TcpServerTest : SuspendTest, TestWithLeakCheck {

    private val tcp = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()

    @Test
    fun testFailedConnection() = test {
        val server = tcp.serverTransport()
        val job = RSocketServer().bind(server) {
            if (config.setupPayload.data.readText() == "ok") {
                RSocketRequestHandler {
                    requestResponse { it }
                }
            } else error("FAILED")
        }

        suspend fun newClient(text: String) = RSocketConnector {
            connectionConfig {
                setupPayload {
                    payload(text)
                }
            }
        }.connect(tcp.clientTransport(server.socket.localAddress))

        val client1 = newClient("ok")
        client1.requestResponse(payload("ok")).release()

        val client2 = newClient("not ok")
        assertFails {
            client2.requestResponse(payload("not ok"))
        }

        val client3 = newClient("ok")

        client3.requestResponse(payload("ok")).release()
        client1.requestResponse(payload("ok")).release()

        assertTrue(client1.isActive)
        assertFalse(client2.isActive)
        assertTrue(client3.isActive)

        assertTrue(job.isActive)
    }

    @Test
    fun testFailedHandler() = test {
        val server = tcp.serverTransport()
        val handlers = mutableListOf<RSocket>()
        val job = RSocketServer().bind(server) {
            RSocketRequestHandler {
                requestResponse { it }
            }.also { handlers += it }
        }

        suspend fun newClient() = RSocketConnector().connect(tcp.clientTransport(server.socket.localAddress))

        val client1 = newClient()

        client1.requestResponse(payload("1")).release()

        val client2 = newClient()

        client2.requestResponse(payload("1")).release()

        handlers[1].job.apply {
            completeExceptionally(IllegalStateException("FAILED"))
            join()
        }

        client1.requestResponse(payload("1")).release()

        assertFails {
            client2.requestResponse(payload("1"))
        }

        val client3 = newClient()

        client3.requestResponse(payload("1")).release()

        client1.requestResponse(payload("1")).release()

        assertTrue(client1.isActive)
        assertFalse(client2.isActive)
        assertTrue(client3.isActive)

        assertTrue(job.isActive)
    }
}
