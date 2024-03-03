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

public sealed interface KtorWebSocketClientTarget : RSocketClientTarget {
    public val url: Url
    public val headers: Headers
}

public sealed interface KtorWebSocketClientTransport : RSocketTransport<
        HttpRequestBuilder.() -> Unit,
        KtorWebSocketClientTarget> {

    public fun target(
        urlString: String,
        request: HttpRequestBuilder.() -> Unit = {},
    ): KtorWebSocketClientTarget = target(
        method = HttpMethod.Get, host = null, port = null, path = null,
        request = {
            url.protocol = URLProtocol.WS
            url.port = port

            url.takeFrom(urlString)
            request()
        },
    )

    public fun target(
        method: HttpMethod = HttpMethod.Get,
        host: String? = null,
        port: Int? = null,
        path: String? = null,
        request: HttpRequestBuilder.() -> Unit = {},
    ): KtorWebSocketClientTarget = target {
        this.method = method
        url("ws", host, port, path)
        request()
    }

    public companion object Factory : RSocketTransportFactory<
            HttpRequestBuilder.() -> Unit,
            KtorWebSocketClientTarget,
            KtorWebSocketClientTransport,
            KtorWebSocketClientTransportBuilder>(::KtorWebSocketClientTransportBuilderImpl)
}

public sealed interface KtorWebSocketClientTransportBuilder : RSocketTransportBuilder<
        HttpRequestBuilder.() -> Unit,
        KtorWebSocketClientTarget,
        KtorWebSocketClientTransport> {

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
        val newContext = httpClient.coroutineContext + context.supervisorContext()
        val newJob = newContext.job
        val httpClientJob = httpClient.coroutineContext.job

        httpClientJob.invokeOnCompletion { newJob.cancel("HttpClient closed", it) }
        newJob.invokeOnCompletion { httpClientJob.cancel("Transport closed", it) }

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
    override fun target(address: HttpRequestBuilder.() -> Unit): KtorWebSocketClientTarget = KtorWebSocketClientTargetImpl(
        coroutineContext = coroutineContext,
        httpClient = httpClient,
        requestBlock = address
    )
}

private class KtorWebSocketClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val httpClient: HttpClient,
    private val requestBlock: HttpRequestBuilder.() -> Unit,
) : KtorWebSocketClientTarget {
    private val requestData: HttpRequestData by lazy {
        HttpRequestBuilder().apply {
            url {
                protocol = URLProtocol.WS
                port = protocol.defaultPort
            }
        }.apply(requestBlock).build()
    }
    override val url: Url get() = requestData.url
    override val headers: Headers get() = requestData.headers

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession {
        ensureActive()

        return KtorWebSocketSession(httpClient.webSocketSession(requestBlock))
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
