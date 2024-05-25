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

package io.rsocket.kotlin.transport.netty.quic

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.ChannelFactory
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.coroutines.*
import kotlin.reflect.*

@OptIn(RSocketTransportApi::class)
public sealed interface NettyQuicClientTransport : RSocketTransport {
    public fun target(remoteAddress: InetSocketAddress): RSocketClientTarget
    public fun target(host: String, port: Int): RSocketClientTarget

    public companion object Factory :
        RSocketTransportFactory<NettyQuicClientTransport, NettyQuicClientTransportBuilder>(::NettyQuicClientTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyQuicClientTransportBuilder : RSocketTransportBuilder<NettyQuicClientTransport> {
    public fun channel(cls: KClass<out DatagramChannel>)
    public fun channelFactory(factory: ChannelFactory<out DatagramChannel>)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: Bootstrap.() -> Unit)
    public fun codec(block: QuicClientCodecBuilder.() -> Unit)
    public fun ssl(block: QuicSslContextBuilder.() -> Unit)
    public fun quicBootstrap(block: QuicChannelBootstrap.() -> Unit)
}

private class NettyQuicClientTransportBuilderImpl : NettyQuicClientTransportBuilder {
    private var channelFactory: ChannelFactory<out DatagramChannel>? = null
    private var eventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (Bootstrap.() -> Unit)? = null
    private var codec: (QuicClientCodecBuilder.() -> Unit)? = null
    private var ssl: (QuicSslContextBuilder.() -> Unit)? = null
    private var quicBootstrap: (QuicChannelBootstrap.() -> Unit)? = null

    override fun channel(cls: KClass<out DatagramChannel>) {
        this.channelFactory = ReflectiveChannelFactory(cls.java)
    }

    override fun channelFactory(factory: ChannelFactory<out DatagramChannel>) {
        this.channelFactory = factory
    }

    override fun eventLoopGroup(group: EventLoopGroup, manage: Boolean) {
        this.eventLoopGroup = group
        this.manageEventLoopGroup = manage
    }

    override fun bootstrap(block: Bootstrap.() -> Unit) {
        bootstrap = block
    }

    override fun codec(block: QuicClientCodecBuilder.() -> Unit) {
        codec = block
    }

    override fun ssl(block: QuicSslContextBuilder.() -> Unit) {
        ssl = block
    }

    override fun quicBootstrap(block: QuicChannelBootstrap.() -> Unit) {
        quicBootstrap = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyQuicClientTransport {
        val codecHandler = QuicClientCodecBuilder().apply {
            // by default, we allow Int.MAX_VALUE of active stream
            initialMaxData(Int.MAX_VALUE.toLong())
            initialMaxStreamDataBidirectionalLocal(Int.MAX_VALUE.toLong())
            initialMaxStreamDataBidirectionalRemote(Int.MAX_VALUE.toLong())
            initialMaxStreamsBidirectional(Int.MAX_VALUE.toLong())
            codec?.invoke(this)
            ssl?.let {
                sslContext(QuicSslContextBuilder.forClient().apply(it).build())
            }
        }.build()
        val bootstrap = Bootstrap().apply {
            bootstrap?.invoke(this)
            localAddress(0)
            handler(codecHandler)
            channelFactory(channelFactory ?: ReflectiveChannelFactory(NioDatagramChannel::class.java))
            group(eventLoopGroup ?: NioEventLoopGroup())
        }

        return NettyQuicClientTransportImpl(
            coroutineContext = context.supervisorContext() + bootstrap.config().group().asCoroutineDispatcher(),
            bootstrap = bootstrap,
            quicBootstrap = quicBootstrap,
            manageBootstrap = manageEventLoopGroup
        )
    }
}

private class NettyQuicClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val quicBootstrap: (QuicChannelBootstrap.() -> Unit)?,
    manageBootstrap: Boolean,
) : NettyQuicClientTransport {
    init {
        if (manageBootstrap) callOnCancellation {
            bootstrap.config().group().shutdownGracefully().awaitFuture()
        }
    }

    override fun target(remoteAddress: InetSocketAddress): NettyQuicClientTargetImpl = NettyQuicClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        bootstrap = bootstrap,
        quicBootstrap = quicBootstrap,
        remoteAddress = remoteAddress
    )

    override fun target(host: String, port: Int): RSocketClientTarget = target(InetSocketAddress(host, port))
}

@OptIn(RSocketTransportApi::class)
private class NettyQuicClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val quicBootstrap: (QuicChannelBootstrap.() -> Unit)?,
    private val remoteAddress: SocketAddress,
) : RSocketClientTarget {
    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
        QuicChannel.newBootstrap(bootstrap.bind().awaitChannel()).also { quicBootstrap?.invoke(it) }
            .handler(
                NettyQuicConnectionInitializer(handler, coroutineContext, isClient = true)
            ).remoteAddress(remoteAddress).connect().awaitFuture()
    }
}
