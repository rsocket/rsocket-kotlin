/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.ktor.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public suspend fun HttpClient.rSocket(
    request: HttpRequestBuilder.() -> Unit,
): RSocket = plugin(RSocketSupport).connector.connect(KtorWebSocketClientTarget(this, request))

public suspend fun HttpClient.rSocket(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
): RSocket = rSocket(method = HttpMethod.Get, host = null, port = null, path = null) {
    url.protocol = URLProtocol.WS
    url.port = url.protocol.defaultPort
    url.takeFrom(urlString)
    request()
}

public suspend fun HttpClient.rSocket(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
): RSocket = rSocket {
    this.method = method
    url("ws", host, port, path)
    request()
}

private class KtorWebSocketClientTarget(
    private val client: HttpClient,
    private val request: HttpRequestBuilder.() -> Unit,
) : RSocketClientTarget {
    override val coroutineContext: CoroutineContext = client.coroutineContext.supervisorContext()

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession {
        ensureActive()

        return KtorWebSocketSession(client.webSocketSession(request))
    }
}
