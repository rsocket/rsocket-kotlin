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

import io.ktor.util.network.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlin.test.*

abstract class TcpServerTest : SuspendTest, TestWithLeakCheck {
    private val testJob = Job()
    private val testContext = testJob + CoroutineExceptionHandler { c, e -> println("$c -> $e") }
    private val address = NetworkAddress("0.0.0.0", PortProvider.next())
    private val serverTransport = TcpServerTransport(address, InUseTrackingPool)
    private val clientTransport = TcpClientTransport(address, testContext, InUseTrackingPool)

    override suspend fun after() {
        testJob.cancelAndJoin()
    }

    @Test
    fun testFailedConnection() = test {
        val server = RSocketServer().bindIn(CoroutineScope(testContext), serverTransport) {
            if (config.setupPayload.data.readText() == "ok") {
                RSocketRequestHandler {
                    requestResponse { it }
                }
            } else error("FAILED")
        }.also { it.serverSocket.await() }

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
        val server = RSocketServer().bindIn(CoroutineScope(testContext), serverTransport) {
            RSocketRequestHandler {
                requestResponse { it }
            }.also { handlers += it }
        }.also { it.serverSocket.await() }

        suspend fun newClient() = RSocketConnector().connect(clientTransport)

        val client1 = newClient()

        client1.requestResponse(payload("1")).release()

        val client2 = newClient()

        client2.requestResponse(payload("1")).release()

        handlers[1].coroutineContext.job.apply {
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
