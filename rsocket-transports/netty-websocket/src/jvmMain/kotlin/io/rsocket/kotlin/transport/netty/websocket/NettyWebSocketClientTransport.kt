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
import kotlin.coroutines.*
import kotlin.reflect.*

public sealed interface NettyWebSocketClientTarget : RSocketClientTarget {
    public val config: WebSocketClientProtocolConfig
}

public sealed interface NettyWebSocketClientTransport : RSocketTransport<
        WebSocketClientProtocolConfig,
        NettyWebSocketClientTarget> {

    public fun target(target: WebSocketClientProtocolConfig.Builder.() -> Unit): NettyWebSocketClientTarget =
        target(WebSocketClientProtocolConfig.newBuilder().apply(target).build())

    public companion object Factory : RSocketTransportFactory<
            WebSocketClientProtocolConfig,
            NettyWebSocketClientTarget,
            NettyWebSocketClientTransport,
            NettyWebSocketClientTransportBuilder>(::NettyWebSocketClientTransportBuilderImpl) {
    }
}

public sealed interface NettyWebSocketClientTransportBuilder :
    RSocketTransportBuilder<WebSocketClientProtocolConfig, NettyWebSocketClientTarget, NettyWebSocketClientTransport> {

    public fun channel(cls: KClass<out DuplexChannel>)
    public fun channelFactory(factory: ChannelFactory<out DuplexChannel>)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: Bootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
    public fun webSockets(block: WebSocketClientProtocolConfig.Builder.() -> Unit)
}

private class NettyWebSocketClientTransportBuilderImpl : NettyWebSocketClientTransportBuilder {
    private var channelFactory: ChannelFactory<out DuplexChannel>? = null
    private var eventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (Bootstrap.() -> Unit)? = null
    private var ssl: (SslContextBuilder.() -> Unit)? = null
    private var webSockets: (WebSocketClientProtocolConfig.Builder.() -> Unit)? = null

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

    override fun webSockets(block: WebSocketClientProtocolConfig.Builder.() -> Unit) {
        webSockets = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyWebSocketClientTransport {
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

        return NettyWebSocketClientTransportImpl(
            coroutineContext = transportContext,
            sslContext = sslContext,
            bootstrap = bootstrap,
            webSocketConfig = webSockets
        )
    }
}

private class NettyWebSocketClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val sslContext: SslContext?,
    private val bootstrap: Bootstrap,
    private val webSocketConfig: (WebSocketClientProtocolConfig.Builder.() -> Unit)?,
) : NettyWebSocketClientTransport {
    override fun target(address: WebSocketClientProtocolConfig): NettyWebSocketClientTarget = NettyWebSocketClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        config = when (webSocketConfig) {
            null -> address
            else -> address.toBuilder().apply(webSocketConfig).build()
        },
        sslContext = sslContext,
        bootstrap = bootstrap,
    )
}

private class NettyWebSocketClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val config: WebSocketClientProtocolConfig,
    private val sslContext: SslContext?,
    private val bootstrap: Bootstrap,
) : NettyWebSocketClientTarget {

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession {
        val remoteAddress = InetSocketAddress(config.webSocketUri().host, config.webSocketUri().port)
        val handler = NettyWebSocketChannelHandler(
            sslContext = sslContext,
            remoteAddress = remoteAddress,
            httpHandler = HttpClientCodec(),
            webSocketHandler = WebSocketClientProtocolHandler(config),
        )
        val future = bootstrap.clone().apply {
            handler(handler)
        }.connect(remoteAddress)

        future.awaitFuture()

        return handler.connect(coroutineContext, future.channel() as DuplexChannel)
    }
}
