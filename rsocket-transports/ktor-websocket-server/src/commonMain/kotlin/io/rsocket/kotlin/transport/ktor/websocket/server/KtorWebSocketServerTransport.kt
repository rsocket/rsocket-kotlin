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

@OptIn(RSocketTransportApi::class)
public sealed interface KtorWebSocketServerInstance : RSocketServerInstance {
    public val connectors: List<EngineConnectorConfig>
    public val path: String
    public val protocol: String?
}

@OptIn(RSocketTransportApi::class)
public sealed interface KtorWebSocketServerTransport : RSocketTransport {

    public fun target(
        host: String = "0.0.0.0",
        port: Int = 80,
        path: String = "",
        protocol: String? = null,
    ): RSocketServerTarget<KtorWebSocketServerInstance>

    public fun target(
        path: String = "",
        protocol: String? = null,
        connectorBuilder: EngineConnectorBuilder.() -> Unit,
    ): RSocketServerTarget<KtorWebSocketServerInstance>

    public fun target(
        connector: EngineConnectorConfig,
        path: String = "",
        protocol: String? = null,
    ): RSocketServerTarget<KtorWebSocketServerInstance>

    public fun target(
        connectors: List<EngineConnectorConfig>,
        path: String = "",
        protocol: String? = null,
    ): RSocketServerTarget<KtorWebSocketServerInstance>

    public companion object Factory :
        RSocketTransportFactory<KtorWebSocketServerTransport, KtorWebSocketServerTransportBuilder>(::KtorWebSocketServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface KtorWebSocketServerTransportBuilder : RSocketTransportBuilder<KtorWebSocketServerTransport> {
    public fun <A : ApplicationEngine, T : ApplicationEngine.Configuration> httpEngine(
        factory: ApplicationEngineFactory<A, T>,
        configure: T.() -> Unit = {},
    )

    public fun webSocketsConfig(block: WebSockets.WebSocketOptions.() -> Unit)
}

private class KtorWebSocketServerTransportBuilderImpl : KtorWebSocketServerTransportBuilder {
    private var httpServerFactory: HttpServerFactory<*, *>? = null
    private var webSocketsConfig: WebSockets.WebSocketOptions.() -> Unit = {}

    override fun <A : ApplicationEngine, T : ApplicationEngine.Configuration> httpEngine(
        factory: ApplicationEngineFactory<A, T>,
        configure: T.() -> Unit,
    ) {
        this.httpServerFactory = HttpServerFactory(factory, configure)
    }

    override fun webSocketsConfig(block: WebSockets.WebSocketOptions.() -> Unit) {
        this.webSocketsConfig = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): KtorWebSocketServerTransport = KtorWebSocketServerTransportImpl(
        // we always add IO - as it's the best choice here, server will use it's own dispatcher anyway
        coroutineContext = context.supervisorContext() + Dispatchers.IO,
        factory = requireNotNull(httpServerFactory) { "httpEngine is required" },
        webSocketsConfig = webSocketsConfig,
    )
}

private class KtorWebSocketServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val factory: HttpServerFactory<*, *>,
    private val webSocketsConfig: WebSockets.WebSocketOptions.() -> Unit,
) : KtorWebSocketServerTransport {
    override fun target(
        connectors: List<EngineConnectorConfig>,
        path: String,
        protocol: String?,
    ): RSocketServerTarget<KtorWebSocketServerInstance> = KtorWebSocketServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        factory = factory,
        webSocketsConfig = webSocketsConfig,
        connectors = connectors,
        path = path,
        protocol = protocol
    )

    override fun target(
        host: String,
        port: Int,
        path: String,
        protocol: String?,
    ): RSocketServerTarget<KtorWebSocketServerInstance> = target(path, protocol) {
        this.host = host
        this.port = port
    }

    override fun target(
        path: String,
        protocol: String?,
        connectorBuilder: EngineConnectorBuilder.() -> Unit,
    ): RSocketServerTarget<KtorWebSocketServerInstance> = target(EngineConnectorBuilder().apply(connectorBuilder), path, protocol)

    override fun target(
        connector: EngineConnectorConfig,
        path: String,
        protocol: String?,
    ): RSocketServerTarget<KtorWebSocketServerInstance> = target(listOf(connector), path, protocol)
}

@OptIn(RSocketTransportApi::class)
private class KtorWebSocketServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val factory: HttpServerFactory<*, *>,
    private val webSocketsConfig: WebSockets.WebSocketOptions.() -> Unit,
    private val connectors: List<EngineConnectorConfig>,
    private val path: String,
    private val protocol: String?,
) : RSocketServerTarget<KtorWebSocketServerInstance> {

    @RSocketTransportApi
    override suspend fun startServer(handler: RSocketConnectionHandler): KtorWebSocketServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        val engine = createServerEngine(handler)
        val resolvedConnectors = startServerEngine(engine)

        return KtorWebSocketServerInstanceImpl(
            coroutineContext = engine.environment.parentCoroutineContext,
            connectors = resolvedConnectors,
            path = path,
            protocol = protocol
        )
    }

    // parentCoroutineContext is the context of server instance
    @RSocketTransportApi
    private fun createServerEngine(handler: RSocketConnectionHandler): ApplicationEngine = factory.createServer(
        applicationEngineEnvironment {
            val target = this@KtorWebSocketServerTargetImpl
            parentCoroutineContext = target.coroutineContext.childContext()
            connectors.addAll(target.connectors)
            module {
                install(WebSockets, webSocketsConfig)
                routing {
                    webSocket(target.path, target.protocol) {
                        handler.handleKtorWebSocketConnection(this)
                    }
                }
            }
        }
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun startServerEngine(
        applicationEngine: ApplicationEngine,
    ): List<EngineConnectorConfig> = launchCoroutine { cont ->
        applicationEngine.start().stopServerOnCancellation()
        cont.resume(applicationEngine.resolvedConnectors()) {
            // will cause stopping of the server
            applicationEngine.environment.parentCoroutineContext.job.cancel("Cancelled", it)
        }
    }
}

private class KtorWebSocketServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val connectors: List<EngineConnectorConfig>,
    override val path: String,
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
