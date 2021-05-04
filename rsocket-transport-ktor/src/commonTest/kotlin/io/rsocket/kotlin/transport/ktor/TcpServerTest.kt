/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin.transport.ktor

import io.ktor.network.selector.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.random.*
import kotlin.test.*

abstract class TcpServerTest(
    private val clientSelector: SelectorManager,
    private val serverSelector: SelectorManager
) : SuspendTest, TestWithLeakCheck {
    private val currentPort = port.incrementAndGet()
    private val serverTransport = TcpServerTransport(serverSelector, port = currentPort)
    private val clientTransport = TcpClientTransport(clientSelector, "0.0.0.0", port = currentPort)

    private lateinit var server: Job

    override suspend fun after() {
        server.cancelAndJoin()
        clientSelector.close()
        serverSelector.close()
    }

    @Test
    fun testFailedConnection() = test {
        server = RSocketServer().bind(serverTransport) {
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
        }.connect(clientTransport)

        val client1 = newClient("ok")
        client1.requestResponse(payload("ok")).release()

        val client2 = newClient("not ok")
        assertFails {
            client2.requestResponse(payload("not ok"))
        }

        val client3 = newClient("ok")

        client3.requestResponse(payload("ok")).release()
        client1.requestResponse(payload("ok")).release()

        assertTrue(client1.job.isActive)
        assertFalse(client2.job.isActive)
        assertTrue(client3.job.isActive)

        assertTrue(server.isActive)

        client1.job.cancelAndJoin()
        client2.job.cancelAndJoin()
        client3.job.cancelAndJoin()
    }

    @Test
    fun testFailedHandler() = test {
        val handlers = mutableListOf<RSocket>()
        server = RSocketServer().bind(serverTransport) {
            RSocketRequestHandler {
                requestResponse { it }
            }.also { handlers += it }
        }

        suspend fun newClient() = RSocketConnector().connect(clientTransport)

        val client1 = newClient()

        client1.requestResponse(payload("1")).release()

        val client2 = newClient()

        client2.requestResponse(payload("1")).release()

        handlers[1].job.apply {
            cancel("FAILED")
            join()
        }

        client1.requestResponse(payload("1")).release()

        assertFails {
            client2.requestResponse(payload("1"))
        }

        val client3 = newClient()

        client3.requestResponse(payload("1")).release()

        client1.requestResponse(payload("1")).release()

        assertTrue(client1.job.isActive)
        assertFalse(client2.job.isActive)
        assertTrue(client3.job.isActive)

        assertTrue(server.isActive)

        client1.job.cancelAndJoin()
        client2.job.cancelAndJoin()
        client3.job.cancelAndJoin()
    }

    companion object {
        private val port = atomic(Random.nextInt(20, 90) * 100)
    }
}
