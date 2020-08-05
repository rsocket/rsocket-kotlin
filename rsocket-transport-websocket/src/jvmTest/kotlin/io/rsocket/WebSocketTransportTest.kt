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

package io.rsocket

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.rsocket.core.*
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

class WebSocketTransportTest : TransportTest() {
    override suspend fun init(): RSocket = httpClient.rSocket(port = 9000)

    companion object {

        private val httpClient = HttpClient(ClientCIO) {
            install(WebSockets)
            install(RSocketClientSupport)
        }

        init {
            embeddedServer(ServerCIO, port = 9000) {
                install(RSocketServerSupport)
                routing {
                    rSocket {
                        TestRSocket()
                    }
                }
            }.start()
        }
    }
}
