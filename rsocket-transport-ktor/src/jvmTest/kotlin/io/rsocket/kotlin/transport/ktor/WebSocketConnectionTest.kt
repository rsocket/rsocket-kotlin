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

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.client.*
import io.rsocket.kotlin.transport.ktor.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.*
import kotlin.test.*
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.features.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.websocket.WebSockets as ServerWebSockets
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport as ClientRSocketSupport
import io.rsocket.kotlin.transport.ktor.server.RSocketSupport as ServerRSocketSupport

class WebSocketConnectionTest : SuspendTest, TestWithLeakCheck {
    private val port = Random.nextInt(20, 90) * 100
    private val client = HttpClient(ClientCIO) {
        install(ClientWebSockets)
        install(ClientRSocketSupport) {
            connector = RSocketConnector {
                connectionConfig {
                    keepAlive = KeepAlive(500)
                }
            }
        }
    }

    private var responderJob: Job? = null

    private val server = embeddedServer(ServerCIO, port) {
        install(ServerWebSockets)
        install(ServerRSocketSupport)
        install(Routing) {
            rSocket {
                RSocketRequestHandler {
                    requestStream {
                        it.release()
                        flow {
                            var i = 0
                            while (true) {
                                emitOrClose(buildPayload { data((++i).toString()) })
                                delay(1000)
                            }
                        }
                    }
                }.also { responderJob = it.job }
            }
        }
    }

    override suspend fun before() {
        server.start()
        delay(1000)
    }

    override suspend fun after() {
        server.stop(0, 0)
    }

    @Test
    fun testWorks() = test {
        val rSocket = client.rSocket(port = port)
        val requesterJob = rSocket.job

        rSocket
            .requestStream(Payload.Empty)
            .take(2)
            .onEach { delay(100); it.release() }
            .collect()

        assertTrue(requesterJob.isActive)
        assertTrue(responderJob?.isActive!!)
    }
}
