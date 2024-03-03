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

import io.ktor.utils.io.core.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import java.net.*
import kotlin.coroutines.*

internal class NettyTcpChannelHandler(
    private val sslContext: SslContext?,
    private val remoteAddress: SocketAddress?,
) : ChannelInitializer<DuplexChannel>() {
    private val frames = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)

    @RSocketTransportApi
    fun connect(
        context: CoroutineContext,
        channel: DuplexChannel,
    ): NettyTcpSession = NettyTcpSession(
        coroutineContext = context,
        channel = channel,
        frames = frames
    )

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
        addLast(
            "rsocket-length-encoder",
            LengthFieldPrepender(
                /* lengthFieldLength = */ 3
            )
        )
        addLast(
            "rsocket-length-decoder",
            LengthFieldBasedFrameDecoder(
                /* maxFrameLength = */ Int.MAX_VALUE,
                /* lengthFieldOffset = */ 0,
                /* lengthFieldLength = */ 3,
                /* lengthAdjustment = */ 0,
                /* initialBytesToStrip = */ 3
            )
        )
        addLast(
            "rsocket-frame-receiver",
            IncomingFramesChannelHandler(frames)
        )
    }

    private class IncomingFramesChannelHandler(
        private val channel: SendChannel<ByteReadPacket>,
    ) : SimpleChannelInboundHandler<ByteBuf>() {
        override fun channelInactive(ctx: ChannelHandlerContext) {
            channel.close() //TODO?
            super.channelInactive(ctx)
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
            channel.trySend(buildPacket {
                writeFully(msg.nioBuffer())
            }).getOrThrow()
        }
    }
}
