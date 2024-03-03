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

import io.ktor.utils.io.core.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.*
import kotlin.coroutines.*


internal class NettyWebSocketChannelHandler(
    private val sslContext: SslContext?,
    private val remoteAddress: SocketAddress?,
    private val httpHandler: ChannelHandler,
    private val webSocketHandler: ChannelHandler,
) : ChannelInitializer<DuplexChannel>() {
    private val frames = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)
    private val handshakeDeferred = CompletableDeferred<Unit>()

    @RSocketTransportApi
    suspend fun connect(
        context: CoroutineContext,
        channel: DuplexChannel,
    ): NettyWebSocketSession {
        handshakeDeferred.await()

        return NettyWebSocketSession(
            coroutineContext = context.childContext(),
            channel = channel,
            frames = frames
        )
    }

    override fun initChannel(ch: DuplexChannel): Unit = with(ch.pipeline()) {
        if (sslContext != null) {
            val sslHandler = if (
                remoteAddress is InetSocketAddress &&
                ch.parent() == null // not server
            ) {
                sslContext.newHandler(ch.alloc(), remoteAddress.hostName, remoteAddress.port)
            } else {
                sslContext.newHandler(ch.alloc())
            }
            addLast("ssl", sslHandler)
        }
        addLast("http", httpHandler)
        addLast(HttpObjectAggregator(65536)) //TODO size?
        addLast("websocket", webSocketHandler)

        addLast(
            "rsocket-frame-receiver",
            IncomingFramesChannelHandler()
        )
    }

    private inner class IncomingFramesChannelHandler : SimpleChannelInboundHandler<WebSocketFrame>() {
        override fun channelInactive(ctx: ChannelHandlerContext) {
            frames.close() //TODO?
            super.channelInactive(ctx)
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: WebSocketFrame) {
            if (msg !is BinaryWebSocketFrame && msg !is TextWebSocketFrame) {
                error("wrong frame type")
            }

            frames.trySend(buildPacket {
                writeFully(msg.content().nioBuffer())
            }).getOrThrow()
        }

        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
            if (
                evt is WebSocketServerProtocolHandler.HandshakeComplete ||
                evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE
            ) {
                handshakeDeferred.complete(Unit)
            }
            //TODO: handle timeout - ?
        }
    }
}
