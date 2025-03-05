/*
 * Copyright 2015-2025 the original author or authors.
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
            coroutineContext = context.supervisorContext() + Dispatchers.Default,
            bootstrap = bootstrap,
            sslContext = sslContext,
        ).also {
            if (manageEventLoopGroup) it.shutdownOnCancellation(bootstrap.config().group())
        }
    }
}

private class NettyTcpClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val sslContext: SslContext?,
) : NettyTcpClientTransport {
    override fun target(remoteAddress: SocketAddress): RSocketClientTarget = NettyTcpClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        bootstrap = bootstrap,
        sslContext = sslContext,
        remoteAddress = remoteAddress,
    )

    override fun target(host: String, port: Int): RSocketClientTarget = target(InetSocketAddress(host, port))
}

@OptIn(RSocketTransportApi::class)
private class NettyTcpClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    bootstrap: Bootstrap,
    sslContext: SslContext?,
    remoteAddress: SocketAddress,
) : RSocketClientTarget {
    private val bootstrap = bootstrap.clone()
        .handler(
            NettyTcpConnectionInitializer(
                parentContext = coroutineContext,
                sslContext = sslContext,
                onConnection = null
            )
        )
        .remoteAddress(remoteAddress)

    @RSocketTransportApi
    override suspend fun connectClient(): RSocketConnection {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        val channel = bootstrap.connect().awaitChannel<Channel>()

        return channel.attr(NettyTcpConnection.ATTRIBUTE).get()
    }
}
