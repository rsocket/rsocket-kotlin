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

package io.rsocket.kotlin.transport.ktor.websocket.client

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.*

class ClientWebSocketTransportTest : TransportTest() {

    private val httpClient = HttpClient(Js) {
        install(WebSockets)
        install(RSocketSupport) { connector = CONNECTOR }
    }

    override suspend fun before() {
        client = httpClient.rSocket(port = 9000)
    }

    override suspend fun after() {
        super.after()
        httpClient.coroutineContext.job.cancelAndJoin()
    }

}
