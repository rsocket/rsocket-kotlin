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

package io.rsocket.kotlin.transport.netty.tcp

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.ChannelFactory
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import java.net.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.reflect.*

public sealed interface NettyTcpServerInstance : RSocketServerInstance {
    public val localAddress: InetSocketAddress
}

public sealed interface NettyTcpServerTarget : RSocketServerTarget<NettyTcpServerInstance> {
    public val localAddress: InetSocketAddress?
}

public sealed interface NettyTcpServerTransport : RSocketTransport<
        InetSocketAddress?,
        NettyTcpServerTarget> {

    public fun target(): NettyTcpServerTarget = target(null)
    public fun target(hostname: String = "0.0.0.0", port: Int = 0): NettyTcpServerTarget = target(InetSocketAddress(hostname, port))

    public companion object Factory : RSocketTransportFactory<
            InetSocketAddress?,
            NettyTcpServerTarget,
            NettyTcpServerTransport,
            NettyTcpServerTransportBuilder>(::NettyTcpServerTransportBuilderImpl)
}

public sealed interface NettyTcpServerTransportBuilder :
    RSocketTransportBuilder<
            InetSocketAddress?,
            NettyTcpServerTarget,
            NettyTcpServerTransport> {

    public fun channel(cls: KClass<out ServerChannel>)
    public fun channelFactory(factory: ChannelFactory<out ServerChannel>)
    public fun eventLoopGroup(parentGroup: EventLoopGroup, childGroup: EventLoopGroup, manage: Boolean)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: ServerBootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
}

private class NettyTcpServerTransportBuilderImpl : NettyTcpServerTransportBuilder {
    private var channelFactory: ChannelFactory<out ServerChannel>? = null
    private var parentEventLoopGroup: EventLoopGroup? = null
    private var childEventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (ServerBootstrap.() -> Unit)? = null
    private var ssl: (SslContextBuilder.() -> Unit)? = null

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

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyTcpServerTransport {
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

        return NettyTcpServerTransportImpl(
            coroutineContext = transportContext,
            bootstrap = bootstrap,
            sslContext = sslContext
        )
    }
}

private class NettyTcpServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
) : NettyTcpServerTransport {
    override fun target(address: InetSocketAddress?): NettyTcpServerTarget {
        return NettyTcpServerTargetImpl(
            coroutineContext = coroutineContext.supervisorContext(),
            localAddress = address,
            bootstrap = bootstrap,
            sslContext = sslContext
        )
    }
}

private class NettyTcpServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val localAddress: InetSocketAddress?,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
) : NettyTcpServerTarget {
    @RSocketTransportApi
    override suspend fun startServer(acceptor: RSocketServerAcceptor): NettyTcpServerInstance {
        ensureActive()

        val instanceContext = coroutineContext.supervisorContext()
        try {
            val future = bootstrap.clone().apply {
                childHandler(AcceptorChannelHandler(instanceContext, sslContext, acceptor))
            }.bind(localAddress ?: InetSocketAddress(0))

            try {
                future.awaitFuture()
            } catch (cause: Throwable) {
                instanceContext.job.cancel("Failed to bind", cause)
                throw cause
            }

            return NettyTcpServerInstanceImpl(
                coroutineContext = instanceContext,
                channel = future.channel() as ServerChannel
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
) : ChannelInitializer<DuplexChannel>(), CoroutineScope {
    override fun initChannel(ch: DuplexChannel) {
        val handler = NettyTcpChannelHandler(
            sslContext = sslContext,
            remoteAddress = null
        )
        ch.pipeline().addLast(handler)
        val connection = handler.connect(coroutineContext.childContext(), ch)
        launch { acceptor.acceptSession(connection) }
    }
}

private class NettyTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    private val channel: ServerChannel,
) : NettyTcpServerInstance {
    override val localAddress: InetSocketAddress get() = channel.localAddress() as InetSocketAddress

    init {
        linkCompletionWith(channel)
    }
}
