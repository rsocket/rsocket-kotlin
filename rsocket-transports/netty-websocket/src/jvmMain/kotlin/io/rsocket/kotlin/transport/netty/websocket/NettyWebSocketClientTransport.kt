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
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.coroutines.*
import kotlin.reflect.*

@OptIn(RSocketTransportApi::class)
public sealed interface NettyWebSocketClientTransport : RSocketTransport {
    public fun target(configure: WebSocketClientProtocolConfig.Builder.() -> Unit): RSocketClientTarget
    public fun target(uri: URI, configure: WebSocketClientProtocolConfig.Builder.() -> Unit = {}): RSocketClientTarget
    public fun target(urlString: String, configure: WebSocketClientProtocolConfig.Builder.() -> Unit = {}): RSocketClientTarget

    public fun target(
        host: String? = null,
        port: Int? = null,
        path: String? = null,
        configure: WebSocketClientProtocolConfig.Builder.() -> Unit = {},
    ): RSocketClientTarget

    public companion object Factory :
        RSocketTransportFactory<NettyWebSocketClientTransport, NettyWebSocketClientTransportBuilder>(::NettyWebSocketClientTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface NettyWebSocketClientTransportBuilder : RSocketTransportBuilder<NettyWebSocketClientTransport> {
    public fun channel(cls: KClass<out DuplexChannel>)
    public fun channelFactory(factory: ChannelFactory<out DuplexChannel>)
    public fun eventLoopGroup(group: EventLoopGroup, manage: Boolean)

    public fun bootstrap(block: Bootstrap.() -> Unit)
    public fun ssl(block: SslContextBuilder.() -> Unit)
    public fun webSocketProtocolConfig(block: WebSocketClientProtocolConfig.Builder.() -> Unit)
}

private class NettyWebSocketClientTransportBuilderImpl : NettyWebSocketClientTransportBuilder {
    private var channelFactory: ChannelFactory<out DuplexChannel>? = null
    private var eventLoopGroup: EventLoopGroup? = null
    private var manageEventLoopGroup: Boolean = false
    private var bootstrap: (Bootstrap.() -> Unit)? = null
    private var ssl: (SslContextBuilder.() -> Unit)? = null
    private var webSocketProtocolConfig: (WebSocketClientProtocolConfig.Builder.() -> Unit)? = null

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

    override fun webSocketProtocolConfig(block: WebSocketClientProtocolConfig.Builder.() -> Unit) {
        webSocketProtocolConfig = block
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NettyWebSocketClientTransport {
        val sslContext = ssl?.let {
            SslContextBuilder.forClient().apply(it).build()
        }

        val bootstrap = Bootstrap().apply {
            bootstrap?.invoke(this)
            channelFactory(channelFactory ?: ReflectiveChannelFactory(NioSocketChannel::class.java))
            group(eventLoopGroup ?: NioEventLoopGroup())
        }

        return NettyWebSocketClientTransportImpl(
            coroutineContext = context.supervisorContext() + bootstrap.config().group().asCoroutineDispatcher(),
            sslContext = sslContext,
            bootstrap = bootstrap,
            webSocketProtocolConfig = webSocketProtocolConfig,
            manageBootstrap = manageEventLoopGroup
        )
    }
}

private class NettyWebSocketClientTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val sslContext: SslContext?,
    private val bootstrap: Bootstrap,
    private val webSocketProtocolConfig: (WebSocketClientProtocolConfig.Builder.() -> Unit)?,
    manageBootstrap: Boolean,
) : NettyWebSocketClientTransport {
    init {
        if (manageBootstrap) callOnCancellation {
            bootstrap.config().group().shutdownGracefully().awaitFuture()
        }
    }

    override fun target(configure: WebSocketClientProtocolConfig.Builder.() -> Unit): RSocketClientTarget {
        val webSocketProtocolConfig = WebSocketClientProtocolConfig.newBuilder().apply {
            // transport config first
            webSocketProtocolConfig?.invoke(this)
            // target config
            configure.invoke(this)
        }.build()
        return NettyWebSocketClientTransportTargetImpl(
            coroutineContext = coroutineContext.supervisorContext(),
            bootstrap = bootstrap,
            sslContext = sslContext,
            webSocketProtocolConfig = webSocketProtocolConfig,
            remoteAddress = InetSocketAddress(
                /* hostname = */ webSocketProtocolConfig.webSocketUri().host,
                /* port = */ webSocketProtocolConfig.webSocketUri().port
            )
        )
    }

    override fun target(uri: URI, configure: WebSocketClientProtocolConfig.Builder.() -> Unit): RSocketClientTarget = target {
        webSocketUri(uri)
    }

    override fun target(urlString: String, configure: WebSocketClientProtocolConfig.Builder.() -> Unit): RSocketClientTarget = target {
        webSocketUri(urlString)
    }

    override fun target(
        host: String?,
        port: Int?,
        path: String?,
        configure: WebSocketClientProtocolConfig.Builder.() -> Unit,
    ): RSocketClientTarget = target {
        webSocketUri(
            URI(
                /* scheme = */ "ws",
                /* userInfo = */ null,
                /* host = */ host ?: "localhost",
                /* port = */ port ?: -1,
                /* path = */ if (path?.startsWith("/") == false) "/$path" else path,
                /* query = */ null,
                /* fragment = */ null
            )
        )
    }
}

@OptIn(RSocketTransportApi::class)
private class NettyWebSocketClientTransportTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val bootstrap: Bootstrap,
    private val sslContext: SslContext?,
    private val webSocketProtocolConfig: WebSocketClientProtocolConfig,
    private val remoteAddress: InetSocketAddress,
) : RSocketClientTarget {

    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
        bootstrap.clone().handler(
            NettyWebSocketClientConnectionInitializer(
                sslContext = sslContext,
                webSocketProtocolConfig = webSocketProtocolConfig,
                remoteAddress = remoteAddress,
                handler = handler,
                coroutineContext = coroutineContext,
            )
        ).connect(remoteAddress).awaitFuture()
    }
}

@RSocketTransportApi
private class NettyWebSocketClientConnectionInitializer(
    sslContext: SslContext?,
    private val webSocketProtocolConfig: WebSocketClientProtocolConfig,
    remoteAddress: InetSocketAddress?,
    handler: RSocketConnectionHandler,
    coroutineContext: CoroutineContext,
) : NettyWebSocketConnectionInitializer(sslContext, remoteAddress, handler, coroutineContext) {
    override fun createHttpHandler(): ChannelHandler = HttpClientCodec()
    override fun createWebSocketHandler(): ChannelHandler = WebSocketClientProtocolHandler(webSocketProtocolConfig)
}
