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

package io.rsocket.kotlin.transport.ktor.websocket.server

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.rsocket.kotlin.transport.ktor.websocket.client.*
import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.*
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.rsocket.kotlin.transport.ktor.websocket.client.RSocketSupport as ClientRSocketSupport
import io.rsocket.kotlin.transport.ktor.websocket.server.RSocketSupport as ServerRSocketSupport

abstract class WebSocketTransportTest(
    clientEngine: HttpClientEngineFactory<*>,
    private val serverEngine: ApplicationEngineFactory<*, *>,
) : TransportTest() {
    private val port = PortProvider.next()

    private val httpClient = HttpClient(clientEngine) {
        install(ClientWebSockets)
        install(ClientRSocketSupport) { connector = CONNECTOR }
    }

    override suspend fun before() {
        testScope.embeddedServer(serverEngine, port) {
            install(ServerWebSockets)
            install(ServerRSocketSupport) { server = SERVER }
            install(Routing) { rSocket(acceptor = ACCEPTOR) }
        }.start()
        client = httpClient.rSocket(port = port)
    }

    override suspend fun after() {
        super.after()
        httpClient.coroutineContext.job.cancelAndJoin()
    }
}
