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
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import java.net.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.reflect.*

public sealed interface NettyWebSocketServerInstance : RSocketServerInstance {
    public val localAddress: InetSocketAddress
    public val config: WebSocketServerProtocolConfig
}

public sealed interface NettyWebSocketServerTarget : RSocketServerTarget<NettyWebSocketServerInstance> {
    public val localAddress: InetSocketAddress?
    public val config: WebSocketServerProtocolConfig
}

public sealed interface NettyWebSocketServerTransport : RSocketTransport<
        InetSocketAddress?,
        NettyWebSocketServerTarget> {

    public fun target(): NettyWebSocketServerTarget = target(null)
    public fun target(hostname: String = "0.0.0.0", port: Int = 0): NettyWebSocketServerTarget = target(InetSocketAddress(hostname, port))

    public companion object Factory : RSocketTransportFactory<
            InetSocketAddress?,
            NettyWebSocketServerTarget,
            NettyWebSocketServerTransport,
            NettyWebSocketServerTransportBuilder>(::NettyWebSocketServerTransportBuilderImpl)
}

public sealed interface NettyWebSocketServerTransportBuilder :
    RSocketTransportBuilder<InetSocketAddress?, NettyWebSocketServerTarget, NettyWebSocketServerTransport> {

    public fun channel(cls: KClass<out ServerChannel>)
    public fun channelFactory(factory: ChannelFactory<out ServerChannel>)
    public fun eventLoopGroup(parentGroup: EventLoopGroup, childGroup: EventLoopGroup, manage: Boolean)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: ServerBootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
    public fun webSockets(block: WebSocketServerProtocolConfig.Builder.() -> Unit)
}

private class NettyWebSocketServerTransportBuilderImpl : NettyWebSocketServerTransportBuilder {
    private var channelFactory: ChannelFactory<out ServerChannel>? = null
    private var parentEventLoopGroup: EventLoopGroup? = null
    private var childEventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (ServerBootstrap.() -> Unit)? = null
    private var ssl: (SslContextBuilder.() -> Unit)? = null
    private var webSockets: (WebSocketServerProtocolConfig.Builder.() -> Unit)? = null

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

    override fun webSockets(block: WebSocketServerProtocolConfig.Builder.() -> Unit) {
        webSockets = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyWebSocketServerTransport {
        val parentGroup = parentEventLoopGroup ?: NioEventLoopGroup()
        val childGroup = childEventLoopGroup ?: NioEventLoopGroup()
        val factory = channelFactory ?: ReflectiveChannelFactory(NioServerSocketChannel::class.java)

        val transportContext = context.supervisorContext() + childGroup.asCoroutineDispatcher()
        if (manageEventLoopGroup) CoroutineScope(transportContext).invokeOnCancellation {
            childGroup.shutdownGracefully().awaitFuture()
            parentGroup.shutdownGracefully().awaitFuture()
        }

        val sslContext = ssl?.let {
            SslContextBuilder
                .forServer(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()))
                .apply(it)
                .build()
        }

        val bootstrap = ServerBootstrap().apply {
            bootstrap?.invoke(this)
            group(parentGroup, childGroup)
            channelFactory(factory)
        }

        return NettyWebSocketServerTransportImpl(
            coroutineContext = transportContext,
            bootstrap = bootstrap,
            sslContext = sslContext,
            webSocketConfig = webSockets
        )
    }
}

private class NettyWebSocketServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
    private val webSocketConfig: (WebSocketServerProtocolConfig.Builder.() -> Unit)?,
) : NettyWebSocketServerTransport {
    override fun target(address: InetSocketAddress?): NettyWebSocketServerTarget = NettyWebSocketServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        localAddress = address,
        config = WebSocketServerProtocolConfig.newBuilder().also { webSocketConfig?.invoke(it) }.build(),
        bootstrap = bootstrap,
        sslContext = sslContext
    )
}

private class NettyWebSocketServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val localAddress: InetSocketAddress?,
    override val config: WebSocketServerProtocolConfig,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
) : NettyWebSocketServerTarget {

    @RSocketTransportApi
    override suspend fun startServer(acceptor: RSocketServerAcceptor): NettyWebSocketServerInstance {
        val instanceContext = coroutineContext.supervisorContext()
        try {
            val future = bootstrap.clone().apply {
                localAddress(localAddress ?: InetSocketAddress(0))
                childHandler(AcceptorChannelHandler(instanceContext, sslContext, acceptor, config))
            }.bind()

            try {
                future.awaitFuture()
            } catch (cause: Throwable) {
                instanceContext.job.cancel("Failed to bind", cause)
                throw cause
            }

            return NettyWebSocketServerInstanceImpl(
                coroutineContext = instanceContext,
                channel = future.channel() as ServerChannel,
                config = config
            )
        } catch (cause: Throwable) {
            instanceContext.job.cancel("Failed to bind", cause)
            throw cause
        }
    }
}

@RSocketTransportApi
private class AcceptorChannelHandler(
    override val coroutineContext: CoroutineContext,
    private val sslContext: SslContext?,
    private val acceptor: RSocketServerAcceptor,
    private val config: WebSocketServerProtocolConfig,
) : ChannelInitializer<DuplexChannel>(), CoroutineScope {
    override fun initChannel(ch: DuplexChannel) {
        val handler = NettyWebSocketChannelHandler(
            sslContext = sslContext,
            remoteAddress = null,
            httpHandler = HttpServerCodec(),
            webSocketHandler = WebSocketServerProtocolHandler(config)
        )
        ch.pipeline().addLast(handler)
        launch {
            acceptor.acceptSession(handler.connect(coroutineContext, ch))
        }
    }
}

private class NettyWebSocketServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    private val channel: ServerChannel,
    override val config: WebSocketServerProtocolConfig,
) : NettyWebSocketServerInstance {
    override val localAddress: InetSocketAddress get() = channel.localAddress() as InetSocketAddress

    init {
        linkCompletionWith(channel)
    }
}
