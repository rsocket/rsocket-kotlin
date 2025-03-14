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
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.reflect.*

@OptIn(RSocketTransportApi::class)
public sealed interface NettyQuicServerInstance : RSocketServerInstance {
    public val localAddress: InetSocketAddress
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyQuicServerTransport : RSocketTransport {
    public fun target(localAddress: InetSocketAddress? = null): RSocketServerTarget<NettyQuicServerInstance>
    public fun target(host: String = "127.0.0.1", port: Int = 0): RSocketServerTarget<NettyQuicServerInstance>

    public companion object Factory :
        RSocketTransportFactory<NettyQuicServerTransport, NettyQuicServerTransportBuilder>(::NettyQuicServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyQuicServerTransportBuilder : RSocketTransportBuilder<NettyQuicServerTransport> {
    public fun channel(cls: KClass<out DatagramChannel>)
    public fun channelFactory(factory: ChannelFactory<out DatagramChannel>)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: Bootstrap.() -> Unit)
    public fun codec(block: QuicServerCodecBuilder.() -> Unit)
    public fun ssl(block: QuicSslContextBuilder.() -> Unit)
}

private class NettyQuicServerTransportBuilderImpl : NettyQuicServerTransportBuilder {
    private var channelFactory: ChannelFactory<out DatagramChannel>? = null
    private var eventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (Bootstrap.() -> Unit)? = null
    private var codec: (QuicServerCodecBuilder.() -> Unit)? = null
    private var ssl: (QuicSslContextBuilder.() -> Unit)? = null

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

    override fun codec(block: QuicServerCodecBuilder.() -> Unit) {
        codec = block
    }

    override fun ssl(block: QuicSslContextBuilder.() -> Unit) {
        ssl = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyQuicServerTransport {
        val codecHandler = fun(connectionInitializer: ChannelHandler): ChannelHandler = QuicServerCodecBuilder().apply {
            // by default, we allow Int.MAX_VALUE of active stream
            initialMaxData(Int.MAX_VALUE.toLong())
            initialMaxStreamDataBidirectionalLocal(Int.MAX_VALUE.toLong())
            initialMaxStreamDataBidirectionalRemote(Int.MAX_VALUE.toLong())
            initialMaxStreamsBidirectional(Int.MAX_VALUE.toLong())
            codec?.invoke(this)
            ssl?.let {
                val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                sslContext(QuicSslContextBuilder.forServer(keyManagerFactory, null).apply(it).build())
            }
            handler(connectionInitializer)
            streamHandler(NettyQuicStreamInitializer)
        }.build()

        val bootstrap = Bootstrap().apply {
            bootstrap?.invoke(this)
            channelFactory(channelFactory ?: ReflectiveChannelFactory(NioDatagramChannel::class.java))
            group(eventLoopGroup ?: NioEventLoopGroup())
        }

        return NettyQuicServerTransportImpl(
            coroutineContext = context.supervisorContext() + bootstrap.config().group().asCoroutineDispatcher(),
            bootstrap = bootstrap,
            codecHandler = codecHandler
        ).also {
            if (manageEventLoopGroup) it.shutdownOnCancellation(bootstrap.config().group())
        }
    }
}

private class NettyQuicServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val codecHandler: (ChannelHandler) -> ChannelHandler,
) : NettyQuicServerTransport {
    override fun target(localAddress: InetSocketAddress?): NettyQuicServerTargetImpl = NettyQuicServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        bootstrap = bootstrap,
        codecHandler = codecHandler,
        localAddress = localAddress ?: InetSocketAddress(0)
    )

    override fun target(host: String, port: Int): RSocketServerTarget<NettyQuicServerInstance> =
        target(InetSocketAddress(host, port))
}

@OptIn(RSocketTransportApi::class)
private class NettyQuicServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val codecHandler: (ChannelHandler) -> ChannelHandler,
    private val localAddress: SocketAddress,
) : RSocketServerTarget<NettyQuicServerInstance> {
    @RSocketTransportApi
    override suspend fun startServer(onConnection: (RSocketConnection) -> Unit): NettyQuicServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        val instanceContext = coroutineContext.childContext()
        val channel = try {
            bootstrap.clone()
                .handler(
                    codecHandler(
                        NettyQuicConnectionInitializer(
                            parentContext = instanceContext.supervisorContext(),
                            onConnection = onConnection
                        )
                    )
                )
                .bind(localAddress)
                .awaitChannel<DatagramChannel>()
        } catch (cause: Throwable) {
            instanceContext.job.cancel("Failed to bind", cause)
            throw cause
        }

        return NettyQuicServerInstanceImpl(
            coroutineContext = instanceContext,
            channel = channel
        )
    }
}

private class NettyQuicServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    private val channel: DatagramChannel,
) : NettyQuicServerInstance {
    override val localAddress: InetSocketAddress get() = channel.localAddress() as InetSocketAddress

    init {
        @OptIn(DelicateCoroutinesApi::class)
        launch(start = CoroutineStart.ATOMIC) {
            try {
                awaitCancellation()
            } finally {
                nonCancellable {
                    channel.close().awaitFuture()
                }
            }
        }
    }
}
