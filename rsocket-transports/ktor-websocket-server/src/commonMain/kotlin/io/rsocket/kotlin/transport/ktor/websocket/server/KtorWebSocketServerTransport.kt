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

package io.rsocket.kotlin.transport.ktor.websocket.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public sealed interface KtorWebSocketServerInstance : RSocketServerInstance {
    public val resolvedConnectors: List<EngineConnectorConfig>
    public val route: String
    public val protocol: String?
}

public sealed interface KtorWebSocketServerTarget : RSocketServerTarget<KtorWebSocketServerInstance> {
    public val connectors: List<EngineConnectorConfig>
    public val route: String
    public val protocol: String?
}

public sealed interface KtorWebSocketServerTransport : RSocketTransport<
        List<EngineConnectorConfig>,
        KtorWebSocketServerTarget> {

    public fun target(port: Int = 80, host: String = "0.0.0.0"): KtorWebSocketServerTarget = target(EngineConnectorBuilder().apply {
        this.host = host
        this.port = port
    })

    public fun target(connector: EngineConnectorConfig): KtorWebSocketServerTarget = target(listOf(connector))

    public companion object Factory :
        RSocketTransportFactory<
                List<EngineConnectorConfig>,
                KtorWebSocketServerTarget,
                KtorWebSocketServerTransport,
                KtorWebSocketServerTransportBuilder>(::KtorWebSocketServerTransportBuilderImpl)
}

public sealed interface KtorWebSocketServerTransportBuilder : RSocketTransportBuilder<
        List<EngineConnectorConfig>,
        KtorWebSocketServerTarget,
        KtorWebSocketServerTransport> {

    public fun <A : ApplicationEngine, T : ApplicationEngine.Configuration> httpEngine(
        factory: ApplicationEngineFactory<A, T>,
        configure: T.() -> Unit = {},
    )

    public fun webSocketsConfig(block: WebSockets.WebSocketOptions.() -> Unit)

    public fun route(path: String)
    public fun protocol(protocol: String?)
}

private class KtorWebSocketServerTransportBuilderImpl : KtorWebSocketServerTransportBuilder {
    private var httpServerFactory: HttpServerFactory<*, *>? = null
    private var webSocketsConfig: WebSockets.WebSocketOptions.() -> Unit = {}
    private var route: String = ""
    private var protocol: String? = null

    override fun <A : ApplicationEngine, T : ApplicationEngine.Configuration> httpEngine(
        factory: ApplicationEngineFactory<A, T>,
        configure: T.() -> Unit,
    ) {
        this.httpServerFactory = HttpServerFactory(factory, configure)
    }

    override fun webSocketsConfig(block: WebSockets.WebSocketOptions.() -> Unit) {
        this.webSocketsConfig = block
    }

    override fun route(path: String) {
        this.route = path
    }

    override fun protocol(protocol: String?) {
        this.protocol = protocol
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): KtorWebSocketServerTransport = KtorWebSocketServerTransportImpl(
        coroutineContext = context.supervisorContext(),
        factory = requireNotNull(httpServerFactory) { "httpEngine is required" },
        webSocketsConfig = webSocketsConfig,
        route = route,
        protocol = protocol,
    )
}

private class KtorWebSocketServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val factory: HttpServerFactory<*, *>,
    private val webSocketsConfig: WebSockets.WebSocketOptions.() -> Unit,
    private val route: String,
    private val protocol: String?,
) : KtorWebSocketServerTransport {
    override fun target(address: List<EngineConnectorConfig>): KtorWebSocketServerTarget = KtorWebSocketServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        connectors = address,
        factory = factory,
        webSocketsConfig = webSocketsConfig,
        route = route,
        protocol = protocol
    )
}

private class KtorWebSocketServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val connectors: List<EngineConnectorConfig>,
    private val factory: HttpServerFactory<*, *>,
    private val webSocketsConfig: WebSockets.WebSocketOptions.() -> Unit,
    override val route: String,
    override val protocol: String?,
) : KtorWebSocketServerTarget {
    @RSocketTransportApi
    override suspend fun startServer(acceptor: RSocketServerAcceptor): KtorWebSocketServerInstance {
        ensureActive()

        val serverInstanceContext = coroutineContext.supervisorContext()

        val environment = applicationEngineEnvironment {
            this.parentCoroutineContext = serverInstanceContext
            this.connectors.addAll(this@KtorWebSocketServerTargetImpl.connectors)
            module {
                install(WebSockets, webSocketsConfig)
                routing {
                    webSocket(route, protocol) {
                        acceptor.acceptSession(KtorWebSocketSession(this))
                        coroutineContext.job.join()
                    }
                }
            }
        }

        val applicationEngine = factory.createServer(environment)

        // start and stop should be executed on Dispatchers.IO - blocking operations
        CoroutineScope(serverInstanceContext).launch(Dispatchers.IO) {
            applicationEngine.start()
            try {
                awaitCancellation()
            } catch (cause: Throwable) {
                withContext(NonCancellable) { applicationEngine.stop() }
                throw cause
            }
        }

        return KtorWebSocketServerInstanceImpl(
            coroutineContext = serverInstanceContext,
            resolvedConnectors = applicationEngine.resolvedConnectors(),
            route = route,
            protocol = protocol
        )
    }
}

private class KtorWebSocketServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val resolvedConnectors: List<EngineConnectorConfig>,
    override val route: String,
    override val protocol: String?,
) : KtorWebSocketServerInstance

private class HttpServerFactory<A : ApplicationEngine, T : ApplicationEngine.Configuration>(
    private val factory: ApplicationEngineFactory<A, T>,
    private val configure: T.() -> Unit = {},
) {
    @RSocketTransportApi
    fun createServer(environment: ApplicationEngineEnvironment): ApplicationEngine {
        return factory.create(environment, configure)
    }
}
