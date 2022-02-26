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

@file:OptIn(TransportApi::class)
@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor.websocket.client

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.*
import kotlinx.coroutines.*

public fun WebSocketClientTransport(
    httpClient: HttpClient,
    request: HttpRequestBuilder.() -> Unit,
): ClientTransport = ClientTransport(httpClient.coroutineContext + SupervisorJob(httpClient.coroutineContext[Job])) {
    val session = httpClient.webSocketSession(request)
    WebSocketConnection(session)
}

public fun WebSocketClientTransport(
    httpClient: HttpClient,
    urlString: String,
    secure: Boolean = false,
    request: HttpRequestBuilder.() -> Unit = {},
): ClientTransport = WebSocketClientTransport(httpClient) {
    url {
        protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        port = url.protocol.defaultPort
        takeFrom(urlString)
    }
    request()
}

public fun WebSocketClientTransport(
    httpClient: HttpClient,
    host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    secure: Boolean = false,
    request: HttpRequestBuilder.() -> Unit = {},
): ClientTransport = WebSocketClientTransport(httpClient) {
    url {
        this.protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        this.port = port
        this.host = host
        this.encodedPath = path
    }
    request()
}
