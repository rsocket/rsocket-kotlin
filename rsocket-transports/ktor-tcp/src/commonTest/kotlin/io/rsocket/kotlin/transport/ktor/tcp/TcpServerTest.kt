/*
 * Copyright 2015-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.sockets.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.*
import kotlin.test.*

class TcpServerTest : SuspendTest, TestWithLeakCheck {
    private val testJob = Job()
    private val testContext = testJob + TestExceptionHandler
    private val address = InetSocketAddress("0.0.0.0", PortProvider.next())
    private val serverTransport = TcpServerTransport(address)
    private val clientTransport = TcpClientTransport(address, testContext)

    override suspend fun after() {
        testJob.cancelAndJoin()
    }

    @Test
    fun testFailedConnection() = test {
        val server = TestServer().bindIn(CoroutineScope(testContext), serverTransport) {
            if (config.setupPayload.data.readText() == "ok") {
                RSocketRequestHandler {
                    requestResponse { it }
                }
            } else error("FAILED")
        }.also { it.serverSocket.await() }

        suspend fun newClient(text: String) = TestConnector {
            connectionConfig {
                setupPayload {
                    payload(text)
                }
            }
        }.connect(clientTransport)

        val client1 = newClient("ok")
        client1.requestResponse(payload("ok")).close()

        val client2 = newClient("not ok")
        assertFails {
            client2.requestResponse(payload("not ok"))
        }

        val client3 = newClient("ok")

        client3.requestResponse(payload("ok")).close()
        client1.requestResponse(payload("ok")).close()

        assertTrue(client1.isActive)
        assertFalse(client2.isActive)
        assertTrue(client3.isActive)

        assertTrue(server.serverSocket.await().socketContext.isActive)
        assertTrue(server.handlerJob.isActive)

        client1.coroutineContext.job.cancelAndJoin()
        client2.coroutineContext.job.cancelAndJoin()
        client3.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun testFailedHandler() = test {
        val handlers = mutableListOf<RSocket>()
        val server = TestServer().bindIn(CoroutineScope(testContext), serverTransport) {
            RSocketRequestHandler {
                requestResponse { it }
            }.also { handlers += it }
        }.also { it.serverSocket.await() }

        suspend fun newClient() = TestConnector().connect(clientTransport)

        val client1 = newClient()

        client1.requestResponse(payload("1")).close()

        val client2 = newClient()

        client2.requestResponse(payload("1")).close()

        handlers[1].coroutineContext.job.apply {
            cancel("FAILED")
            join()
        }

        client1.requestResponse(payload("1")).close()

        assertFails {
            client2.requestResponse(payload("1"))
        }

        val client3 = newClient()

        client3.requestResponse(payload("1")).close()

        client1.requestResponse(payload("1")).close()

        assertTrue(client1.isActive)
        assertFalse(client2.isActive)
        assertTrue(client3.isActive)

        assertTrue(server.serverSocket.await().socketContext.isActive)
        assertTrue(server.handlerJob.isActive)

        client1.coroutineContext.job.cancelAndJoin()
        client2.coroutineContext.job.cancelAndJoin()
        client3.coroutineContext.job.cancelAndJoin()
    }
}
