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

import io.ktor.client.engine.*
import io.ktor.server.engine.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.websocket.client.*
import io.rsocket.kotlin.transport.tests.*

abstract class WebSocketTransportTest(
    private val clientEngine: HttpClientEngineFactory<*>,
    private val serverEngine: ApplicationEngineFactory<*, *>,
) : TransportTest() {
    override suspend fun before() {
        val port = PortProvider.next()
        startServer(
            WebSocketServerTransport(serverEngine, port = port, pool = InUseTrackingPool)
        )
        client = connectClient(
            WebSocketClientTransport(clientEngine, port = port, context = testContext, pool = InUseTrackingPool)
        )
    }
}
