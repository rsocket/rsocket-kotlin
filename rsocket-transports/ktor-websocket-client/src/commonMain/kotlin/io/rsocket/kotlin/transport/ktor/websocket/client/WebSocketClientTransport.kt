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

@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor.websocket.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("DEPRECATION_ERROR")
@Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API, use KtorWebSocketClientTransport")
public fun <T : HttpClientEngineConfig> WebSocketClientTransport(
    engineFactory: HttpClientEngineFactory<T>,
    context: CoroutineContext = EmptyCoroutineContext,
    engine: T.() -> Unit = {},
    webSockets: WebSockets.Config.() -> Unit = {},
    request: HttpRequestBuilder.() -> Unit,
): ClientTransport {
    val clientEngine = engineFactory.create(engine)

    val transportJob = SupervisorJob(context[Job])
    val transportContext = clientEngine.coroutineContext + context + transportJob

    val httpClient = HttpClient(clientEngine) {
        WebSockets(webSockets)
    }

    Job(transportJob).invokeOnCompletion {
        httpClient.close()
        httpClient.cancel()
        clientEngine.close()
        clientEngine.cancel()
    }

    return ClientTransport(transportContext) {
        val session = httpClient.webSocketSession(request)
        WebSocketConnection(session)
    }
}

@Suppress("DEPRECATION_ERROR")
@Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API, use KtorWebSocketClientTransport")
public fun <T : HttpClientEngineConfig> WebSocketClientTransport(
    engineFactory: HttpClientEngineFactory<T>,
    urlString: String, secure: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    engine: HttpClientEngineConfig.() -> Unit = {},
    webSockets: WebSockets.Config.() -> Unit = {},
    request: HttpRequestBuilder.() -> Unit = {},
): ClientTransport = WebSocketClientTransport(engineFactory, context, engine, webSockets) {
    url {
        this.protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        this.port = protocol.defaultPort
        takeFrom(urlString)
    }
    request()
}

@Suppress("DEPRECATION_ERROR")
@Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API, use KtorWebSocketClientTransport")
public fun <T : HttpClientEngineConfig> WebSocketClientTransport(
    engineFactory: HttpClientEngineFactory<T>,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    secure: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    engine: HttpClientEngineConfig.() -> Unit = {},
    webSockets: WebSockets.Config.() -> Unit = {},
    request: HttpRequestBuilder.() -> Unit = {},
): ClientTransport = WebSocketClientTransport(engineFactory, context, engine, webSockets) {
    url {
        this.protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
        this.port = protocol.defaultPort
        set(host = host, port = port, path = path)
    }
    request()
}
