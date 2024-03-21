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

package io.rsocket.kotlin.transport.netty.websocket

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.ChannelFactory
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import java.net.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.reflect.*

@OptIn(RSocketTransportApi::class)
public sealed interface NettyWebSocketServerInstance : RSocketServerInstance {
    public val localAddress: InetSocketAddress
    public val webSocketProtocolConfig: WebSocketServerProtocolConfig
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyWebSocketServerTransport : RSocketTransport {

    public fun target(
        localAddress: InetSocketAddress? = null,
        path: String = "",
        protocol: String? = null,
    ): RSocketServerTarget<NettyWebSocketServerInstance>

    public fun target(
        host: String = "0.0.0.0",
        port: Int = 0,
        path: String = "",
        protocol: String? = null,
    ): RSocketServerTarget<NettyWebSocketServerInstance>

    public companion object Factory :
        RSocketTransportFactory<NettyWebSocketServerTransport, NettyWebSocketServerTransportBuilder>(::NettyWebSocketServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyWebSocketServerTransportBuilder : RSocketTransportBuilder<NettyWebSocketServerTransport> {
    public fun channel(cls: KClass<out ServerChannel>)
    public fun channelFactory(factory: ChannelFactory<out ServerChannel>)
    public fun eventLoopGroup(parentGroup: EventLoopGroup, childGroup: EventLoopGroup, manage: Boolean)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: ServerBootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
    public fun webSocketProtocolConfig(block: WebSocketServerProtocolConfig.Builder.() -> Unit)
}

private class NettyWebSocketServerTransportBuilderImpl : NettyWebSocketServerTransportBuilder {
    private var channelFactory: ChannelFactory<out ServerChannel>? = null
    private var parentEventLoopGroup: EventLoopGroup? = null
    private var childEventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (ServerBootstrap.() -> Unit)? = null
    private var ssl: (SslContextBuilder.() -> Unit)? = null
    private var webSocketProtocolConfig: (WebSocketServerProtocolConfig.Builder.() -> Unit)? = null

    override fun channel(cls: KClass<out ServerChannel>) {
        this.channelFactory = ReflectiveChannelFactory(cls.java)
    }

    override fun channelFactory(factory: ChannelFactory<out ServerChannel>) {
        this.channelFactory = factory
    }

    override fun eventLoopGroup(parentGroup: EventLoopGroup, childGroup: EventLoopGroup, manage: Boolean) {
        this.parentEventLoopGroup = parentGroup
        this.childEventLoopGroup = childGroup
        this.manageEventLoopGroup = manage
    }

    override fun eventLoopGroup(group: EventLoopGroup, manage: Boolean) {
        this.parentEventLoopGroup = group
        this.childEventLoopGroup = group
        this.manageEventLoopGroup = manage
    }

    override fun bootstrap(block: ServerBootstrap.() -> Unit) {
        bootstrap = block
    }

    override fun ssl(block: SslContextBuilder.() -> Unit) {
        ssl = block
    }

    override fun webSocketProtocolConfig(block: WebSocketServerProtocolConfig.Builder.() -> Unit) {
        webSocketProtocolConfig = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyWebSocketServerTransport {
        val sslContext = ssl?.let {
            SslContextBuilder
                .forServer(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()))
                .apply(it)
                .build()
        }

        val bootstrap = ServerBootstrap().apply {
            bootstrap?.invoke(this)
            channelFactory(channelFactory ?: ReflectiveChannelFactory(NioServerSocketChannel::class.java))
            group(parentEventLoopGroup ?: NioEventLoopGroup(), childEventLoopGroup ?: NioEventLoopGroup())
        }

        return NettyWebSocketServerTransportImpl(
            coroutineContext = context.supervisorContext() + bootstrap.config().childGroup().asCoroutineDispatcher(),
            bootstrap = bootstrap,
            sslContext = sslContext,
            webSocketProtocolConfig = webSocketProtocolConfig,
            manageBootstrap = manageEventLoopGroup
        )
    }
}

private class NettyWebSocketServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
    private val webSocketProtocolConfig: (WebSocketServerProtocolConfig.Builder.() -> Unit)?,
    manageBootstrap: Boolean,
) : NettyWebSocketServerTransport {
    init {
        if (manageBootstrap) callOnCancellation {
            bootstrap.config().childGroup().shutdownGracefully().awaitFuture()
            bootstrap.config().group().shutdownGracefully().awaitFuture()
        }
    }

    override fun target(
        localAddress: InetSocketAddress?,
        path: String,
        protocol: String?,
    ): RSocketServerTarget<NettyWebSocketServerInstance> = NettyWebSocketServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        bootstrap = bootstrap,
        sslContext = sslContext,
        webSocketProtocolConfig = WebSocketServerProtocolConfig.newBuilder().apply {
            webSocketProtocolConfig?.invoke(this)
            websocketPath(if (!path.startsWith("/")) "/$path" else path)
            subprotocols(protocol)
        }.build(),
        localAddress = localAddress ?: InetSocketAddress(0)
    )

    override fun target(host: String, port: Int, path: String, protocol: String?): RSocketServerTarget<NettyWebSocketServerInstance> =
        target(InetSocketAddress(host, port), path, protocol)
}

@OptIn(RSocketTransportApi::class)
private class NettyWebSocketServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
    private val webSocketProtocolConfig: WebSocketServerProtocolConfig,
    private val localAddress: SocketAddress?,
) : RSocketServerTarget<NettyWebSocketServerInstance> {

    @RSocketTransportApi
    override suspend fun startServer(handler: RSocketConnectionHandler): NettyWebSocketServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        val instanceContext = coroutineContext.childContext()
        val channel = try {
            bootstrap.clone().childHandler(
                NettyWebSocketServerConnectionInitializer(
                    sslContext = sslContext,
                    webSocketProtocolConfig = webSocketProtocolConfig,
                    handler = handler,
                    coroutineContext = instanceContext.supervisorContext()
                )
            ).bind(localAddress).awaitChannel()
        } catch (cause: Throwable) {
            instanceContext.job.cancel("Failed to bind", cause)
            throw cause
        }

        // TODO: handle server closure
        return NettyWebSocketServerInstanceImpl(
            coroutineContext = instanceContext,
            localAddress = (channel as ServerChannel).localAddress() as InetSocketAddress,
            webSocketProtocolConfig = webSocketProtocolConfig
        )
    }
}

@RSocketTransportApi
private class NettyWebSocketServerConnectionInitializer(
    sslContext: SslContext?,
    private val webSocketProtocolConfig: WebSocketServerProtocolConfig,
    handler: RSocketConnectionHandler,
    coroutineContext: CoroutineContext,
) : NettyWebSocketConnectionInitializer(sslContext, null, handler, coroutineContext) {
    override fun createHttpHandler(): ChannelHandler = HttpServerCodec()
    override fun createWebSocketHandler(): ChannelHandler = WebSocketServerProtocolHandler(webSocketProtocolConfig)
}

private class NettyWebSocketServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val localAddress: InetSocketAddress,
    override val webSocketProtocolConfig: WebSocketServerProtocolConfig,
) : NettyWebSocketServerInstance
