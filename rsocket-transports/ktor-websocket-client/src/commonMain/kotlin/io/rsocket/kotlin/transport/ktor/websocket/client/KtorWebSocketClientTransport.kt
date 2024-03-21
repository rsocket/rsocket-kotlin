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

package io.rsocket.kotlin.transport.ktor.websocket.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(RSocketTransportApi::class)
public sealed interface KtorWebSocketClientTransport : RSocketTransport {
    public fun target(request: HttpRequestBuilder.() -> Unit): RSocketClientTarget
    public fun target(urlString: String, request: HttpRequestBuilder.() -> Unit = {}): RSocketClientTarget

    public fun target(
        method: HttpMethod = HttpMethod.Get,
        host: String? = null,
        port: Int? = null,
        path: String? = null,
        request: HttpRequestBuilder.() -> Unit = {},
    ): RSocketClientTarget

    public companion object Factory :
        RSocketTransportFactory<KtorWebSocketClientTransport, KtorWebSocketClientTransportBuilder>(::KtorWebSocketClientTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface KtorWebSocketClientTransportBuilder : RSocketTransportBuilder<KtorWebSocketClientTransport> {
    public fun httpEngine(configure: HttpClientEngineConfig.() -> Unit)
    public fun httpEngine(engine: HttpClientEngine, configure: HttpClientEngineConfig.() -> Unit = {})
    public fun <T : HttpClientEngineConfig> httpEngine(factory: HttpClientEngineFactory<T>, configure: T.() -> Unit = {})

    public fun webSocketsConfig(block: WebSockets.Config.() -> Unit)
}

private class KtorWebSocketClientTransportBuilderImpl : KtorWebSocketClientTransportBuilder {
    private var httpClientFactory: HttpClientFactory = HttpClientFactory.Default
    private var webSocketsConfig: WebSockets.Config.() -> Unit = {}

    override fun httpEngine(configure: HttpClientEngineConfig.() -> Unit) {
        this.httpClientFactory = HttpClientFactory.FromConfiguration(configure)
    }

    override fun httpEngine(engine: HttpClientEngine, configure: HttpClientEngineConfig.() -> Unit) {
        this.httpClientFactory = HttpClientFactory.FromEngine(engine, configure)
    }

    override fun <T : HttpClientEngineConfig> httpEngine(factory: HttpClientEngineFactory<T>, configure: T.() -> Unit) {
        this.httpClientFactory = HttpClientFactory.FromFactory(factory, configure)
    }

    override fun webSocketsConfig(block: WebSockets.Config.() -> Unit) {
        this.webSocketsConfig = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): KtorWebSocketClientTransport {
        val httpClient = httpClientFactory.createHttpClient {
            install(WebSockets, webSocketsConfig)
        }
        // only dispatcher of a client is used - it looks like it's Dispatchers.IO now
        val newContext = context.supervisorContext() + (httpClient.coroutineContext[ContinuationInterceptor] ?: EmptyCoroutineContext)
        val newJob = newContext.job
        val httpClientJob = httpClient.coroutineContext.job

        httpClientJob.invokeOnCompletion { newJob.cancel("HttpClient closed", it) }
        newJob.invokeOnCompletion { httpClientJob.cancel("KtorWebSocketClientTransport closed", it) }

        return KtorWebSocketClientTransportImpl(
            coroutineContext = newContext,
            httpClient = httpClient,
        )
    }
}

private class KtorWebSocketClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val httpClient: HttpClient,
) : KtorWebSocketClientTransport {
    override fun target(request: HttpRequestBuilder.() -> Unit): RSocketClientTarget = KtorWebSocketClientTargetImpl(
        coroutineContext = coroutineContext,
        httpClient = httpClient,
        request = request
    )

    override fun target(
        urlString: String,
        request: HttpRequestBuilder.() -> Unit,
    ): RSocketClientTarget = target(
        method = HttpMethod.Get, host = null, port = null, path = null,
        request = {
            url.protocol = URLProtocol.WS
            url.port = port

            url.takeFrom(urlString)
            request()
        },
    )

    override fun target(
        method: HttpMethod,
        host: String?,
        port: Int?,
        path: String?,
        request: HttpRequestBuilder.() -> Unit,
    ): RSocketClientTarget = target {
        this.method = method
        url("ws", host, port, path)
        request()
    }
}

@OptIn(RSocketTransportApi::class)
private class KtorWebSocketClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val httpClient: HttpClient,
    private val request: HttpRequestBuilder.() -> Unit,
) : RSocketClientTarget {

    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
        httpClient.webSocket(request) {
            handler.handleKtorWebSocketConnection(this)
        }
    }
}

private sealed class HttpClientFactory {
    abstract fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

    object Default : HttpClientFactory() {
        override fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(block)
    }

    class FromConfiguration(
        private val configure: HttpClientEngineConfig.() -> Unit,
    ) : HttpClientFactory() {
        override fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient {
            engine(configure)
            block()
        }
    }

    class FromEngine(
        private val engine: HttpClientEngine,
        private val configure: HttpClientEngineConfig.() -> Unit,
    ) : HttpClientFactory() {
        override fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(engine) {
            engine(configure)
            block()
        }
    }

    class FromFactory<T : HttpClientEngineConfig>(
        private val factory: HttpClientEngineFactory<T>,
        private val configure: T.() -> Unit,
    ) : HttpClientFactory() {
        override fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(factory) {
            engine(configure)
            block()
        }
    }
}
