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
import io.ktor.client.engine.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.client.*
import io.rsocket.kotlin.transport.ktor.server.*
import kotlinx.coroutines.*
import io.ktor.client.features.websocket.WebSockets as ClientWebSockets
import io.ktor.websocket.WebSockets as ServerWebSockets
import io.rsocket.kotlin.transport.ktor.client.RSocketSupport as ClientRSocketSupport
import io.rsocket.kotlin.transport.ktor.server.RSocketSupport as ServerRSocketSupport

abstract class WebSocketTransportTest(
    clientEngine: HttpClientEngineFactory<*>,
    serverEngine: ApplicationEngineFactory<*, *>,
) : TransportTest() {
    private val port = PortProvider.next()
    private val testJob = Job()

    private val httpClient = HttpClient(clientEngine) {
        install(ClientWebSockets)
        install(ClientRSocketSupport) { connector = CONNECTOR }
    }

    private val server = (GlobalScope + testJob).embeddedServer(serverEngine, port) {
        install(ServerWebSockets)
        install(ServerRSocketSupport) { server = SERVER }
        install(Routing) { rSocket(acceptor = ACCEPTOR) }
    }

    override suspend fun before() {
        super.before()

        server.start()
        client = trySeveralTimes { httpClient.rSocket(port = port) }
    }

    override suspend fun after() {
        super.after()

        server.stop(200, 1000)
        testJob.cancelAndJoin()
        httpClient.close()
        httpClient.coroutineContext.job.cancelAndJoin()
    }

    private suspend inline fun <R> trySeveralTimes(block: () -> R): R {
        lateinit var error: Throwable
        repeat(10) {
            try {
                return block()
            } catch (e: Throwable) {
                error = e
                delay(500) //sometimes address isn't yet available (server isn't started)
            }
        }
        throw error
    }
}
