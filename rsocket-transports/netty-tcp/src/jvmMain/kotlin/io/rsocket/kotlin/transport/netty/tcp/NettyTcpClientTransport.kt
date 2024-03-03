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
import kotlin.coroutines.*
import kotlin.reflect.*

public sealed interface NettyTcpClientTarget : RSocketClientTarget {
    public val remoteAddress: SocketAddress
}

public sealed interface NettyTcpClientTransport : RSocketTransport<
        InetSocketAddress,
        NettyTcpClientTarget> {

    public fun target(hostname: String, port: Int): NettyTcpClientTarget = target(InetSocketAddress(hostname, port))

    public companion object Factory : RSocketTransportFactory<
            InetSocketAddress,
            NettyTcpClientTarget,
            NettyTcpClientTransport,
            NettyTcpClientTransportBuilder>(::NettyTcpClientTransportBuilderImpl)
}

public sealed interface NettyTcpClientTransportBuilder : RSocketTransportBuilder<
        InetSocketAddress,
        NettyTcpClientTarget,
        NettyTcpClientTransport> {

    public fun channel(cls: KClass<out DuplexChannel>)
    public fun channelFactory(factory: ChannelFactory<out DuplexChannel>)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: Bootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
}

private class NettyTcpClientTransportBuilderImpl : NettyTcpClientTransportBuilder {
    private var channelFactory: ChannelFactory<out DuplexChannel>? = null
    private var eventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (Bootstrap.() -> Unit)? = null
    private var ssl: (SslContextBuilder.() -> Unit)? = null

    override fun channel(cls: KClass<out DuplexChannel>) {
        this.channelFactory = ReflectiveChannelFactory(cls.java)
    }

    override fun channelFactory(factory: ChannelFactory<out DuplexChannel>) {
        this.channelFactory = factory
    }

    override fun eventLoopGroup(group: EventLoopGroup, manage: Boolean) {
        this.eventLoopGroup = group
        this.manageEventLoopGroup = manage
    }

    override fun bootstrap(block: Bootstrap.() -> Unit) {
        bootstrap = block
    }

    override fun ssl(block: SslContextBuilder.() -> Unit) {
        ssl = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyTcpClientTransport {
        val group = eventLoopGroup ?: NioEventLoopGroup()
        val factory = channelFactory ?: ReflectiveChannelFactory(NioSocketChannel::class.java)

        val transportContext = context.supervisorContext() + group.asCoroutineDispatcher()
        if (manageEventLoopGroup) CoroutineScope(transportContext).invokeOnCancellation {
            group.shutdownGracefully().awaitFuture()
        }

        val sslContext = ssl?.let {
            SslContextBuilder
                .forClient()
                .apply(it)
                .build()
        }

        val bootstrap = Bootstrap().apply {
            bootstrap?.invoke(this)
            group(group)
            channelFactory(factory)
        }

        return NettyTcpClientTransportImpl(
            coroutineContext = transportContext,
            sslContext = sslContext,
            bootstrap = bootstrap
        )
    }
}

private class NettyTcpClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val sslContext: SslContext?,
    private val bootstrap: Bootstrap,
) : NettyTcpClientTransport {
    override fun target(address: InetSocketAddress): NettyTcpClientTarget = NettyTcpClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        remoteAddress = address,
        sslContext = sslContext,
        bootstrap = bootstrap
    )
}

private class NettyTcpClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val remoteAddress: SocketAddress,
    private val sslContext: SslContext?,
    private val bootstrap: Bootstrap,
) : NettyTcpClientTarget {
    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession {
        ensureActive()

        val handler = NettyTcpChannelHandler(
            sslContext = sslContext,
            remoteAddress = remoteAddress
        )
        val future = bootstrap.clone().apply {
            handler(handler)
        }.connect(remoteAddress)

        future.awaitFuture()

        return handler.connect(coroutineContext.childContext(), future.channel() as DuplexChannel)
    }
}
