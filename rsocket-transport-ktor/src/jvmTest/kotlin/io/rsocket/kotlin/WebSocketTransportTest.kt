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

package io.rsocket.kotlin

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.rsocket.kotlin.core.*
import kotlinx.coroutines.*
import io.ktor.client.features.websocket.WebSockets as ClientWebSockets
import io.ktor.websocket.WebSockets as ServerWebSockets

abstract class WebSocketTransportTest(
    clientEngine: HttpClientEngineFactory<*>,
    serverEngine: ApplicationEngineFactory<*, *>,
) : TransportTest() {

    private val httpClient = HttpClient(clientEngine) {
        install(ClientWebSockets)
        install(RSocketClientSupport) {
            fromConfig(CONNECTOR_CONFIG)
        }
    }

    private val server = embeddedServer(serverEngine, port = 9000) {
        install(ServerWebSockets)
        install(RSocketServerSupport) {
            fromConfig(SERVER_CONFIG)
        }
        routing {
            rSocket(acceptor = ACCEPTOR)
        }
    }

    override suspend fun before() {
        super.before()

        trySeveralTimes { server.start() }
        client = trySeveralTimes { httpClient.rSocket(port = 9000) }
    }

    override suspend fun after() {
        super.after()

        server.stop(0, 0)
    }

    private suspend inline fun <R> trySeveralTimes(block: () -> R): R {
        lateinit var error: Throwable
        repeat(5) {
            try {
                return block()
            } catch (e: Throwable) {
                error = e
                delay(500) //sometimes address isn't yet available
            }
        }
        throw error
    }
}
