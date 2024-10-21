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
import io.netty.channel.socket.nio.*
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
public sealed interface NettyTcpServerInstance : RSocketServerInstance {
    public val localAddress: SocketAddress
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyTcpServerTransport : RSocketTransport {
    public fun target(localAddress: SocketAddress? = null): RSocketServerTarget<NettyTcpServerInstance>
    public fun target(host: String = "0.0.0.0", port: Int = 0): RSocketServerTarget<NettyTcpServerInstance>

    public companion object Factory :
        RSocketTransportFactory<NettyTcpServerTransport, NettyTcpServerTransportBuilder>(::NettyTcpServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyTcpServerTransportBuilder : RSocketTransportBuilder<NettyTcpServerTransport> {
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
    private var manageEventLoopGroup: Boolean = true
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
        val sslContext = ssl?.let {
            SslContextBuilder.forServer(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())).apply(it).build()
        }

        val bootstrap = ServerBootstrap().apply {
            bootstrap?.invoke(this)
            channelFactory(channelFactory ?: ReflectiveChannelFactory(NioServerSocketChannel::class.java))
            group(parentEventLoopGroup ?: NioEventLoopGroup(), childEventLoopGroup ?: NioEventLoopGroup())
        }

        return NettyTcpServerTransportImpl(
            coroutineContext = context.supervisorContext() + bootstrap.config().childGroup().asCoroutineDispatcher(),
            bootstrap = bootstrap,
            sslContext = sslContext,
            manageBootstrap = manageEventLoopGroup
        )
    }
}

private class NettyTcpServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
    manageBootstrap: Boolean,
) : NettyTcpServerTransport {
    init {
        if (manageBootstrap) callOnCancellation {
            bootstrap.config().childGroup().shutdownGracefully().awaitFuture()
            bootstrap.config().group().shutdownGracefully().awaitFuture()
        }
    }

    override fun target(localAddress: SocketAddress?): NettyTcpServerTargetImpl = NettyTcpServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        bootstrap = bootstrap,
        sslContext = sslContext,
        localAddress = localAddress ?: InetSocketAddress(0),
    )

    override fun target(host: String, port: Int): RSocketServerTarget<NettyTcpServerInstance> =
        target(InetSocketAddress(host, port))
}

@OptIn(RSocketTransportApi::class)
private class NettyTcpServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: ServerBootstrap,
    private val sslContext: SslContext?,
    private val localAddress: SocketAddress,
) : RSocketServerTarget<NettyTcpServerInstance> {
    @RSocketTransportApi
    override suspend fun startServer(handler: RSocketConnectionHandler): NettyTcpServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        val instanceContext = coroutineContext.childContext()
        val channel = try {
            bootstrap.clone().childHandler(
                NettyTcpConnectionInitializer(
                    sslContext = sslContext,
                    remoteAddress = null,
                    handler = handler,
                    coroutineContext = instanceContext.supervisorContext()
                )
            ).bind(localAddress).awaitChannel()
        } catch (cause: Throwable) {
            instanceContext.job.cancel("Failed to bind", cause)
            throw cause
        }

        // TODO: handle server closure
        return NettyTcpServerInstanceImpl(
            coroutineContext = instanceContext,
            localAddress = (channel as ServerChannel).localAddress()
        )
    }
}

private class NettyTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val localAddress: SocketAddress,
) : NettyTcpServerInstance
