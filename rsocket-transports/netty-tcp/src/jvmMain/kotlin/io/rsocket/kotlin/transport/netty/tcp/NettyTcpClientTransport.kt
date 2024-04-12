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
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.coroutines.*
import kotlin.reflect.*

@OptIn(RSocketTransportApi::class)
public sealed interface NettyTcpClientTransport : RSocketTransport {
    public fun target(remoteAddress: SocketAddress): RSocketClientTarget
    public fun target(host: String, port: Int): RSocketClientTarget

    public companion object Factory :
        RSocketTransportFactory<NettyTcpClientTransport, NettyTcpClientTransportBuilder>(::NettyTcpClientTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyTcpClientTransportBuilder : RSocketTransportBuilder<NettyTcpClientTransport> {
    public fun channel(cls: KClass<out DuplexChannel>)
    public fun channelFactory(factory: ChannelFactory<out DuplexChannel>)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: Bootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
}

private class NettyTcpClientTransportBuilderImpl : NettyTcpClientTransportBuilder {
    private var channelFactory: ChannelFactory<out DuplexChannel>? = null
    private var eventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = true
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
        val sslContext = ssl?.let {
            SslContextBuilder.forClient().apply(it).build()
        }

        val bootstrap = Bootstrap().apply {
            bootstrap?.invoke(this)
            channelFactory(channelFactory ?: ReflectiveChannelFactory(NioSocketChannel::class.java))
            group(eventLoopGroup ?: NioEventLoopGroup())
        }

        return NettyTcpClientTransportImpl(
            coroutineContext = context.supervisorContext() + bootstrap.config().group().asCoroutineDispatcher(),
            sslContext = sslContext,
            bootstrap = bootstrap,
            manageBootstrap = manageEventLoopGroup
        )
    }
}

private class NettyTcpClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val sslContext: SslContext?,
    private val bootstrap: Bootstrap,
    manageBootstrap: Boolean,
) : NettyTcpClientTransport {
    init {
        if (manageBootstrap) callOnCancellation {
            bootstrap.config().group().shutdownGracefully().awaitFuture()
        }
    }

    override fun target(remoteAddress: SocketAddress): NettyTcpClientTargetImpl = NettyTcpClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        bootstrap = bootstrap,
        sslContext = sslContext,
        remoteAddress = remoteAddress
    )

    override fun target(host: String, port: Int): RSocketClientTarget = target(InetSocketAddress(host, port))
}

@OptIn(RSocketTransportApi::class)
private class NettyTcpClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val sslContext: SslContext?,
    private val remoteAddress: SocketAddress,
) : RSocketClientTarget {
    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
        bootstrap.clone().handler(
            NettyTcpConnectionInitializer(
                sslContext = sslContext,
                remoteAddress = remoteAddress as? InetSocketAddress,
                handler = handler,
                coroutineContext = coroutineContext
            )
        ).connect(remoteAddress).awaitFuture()
    }
}
