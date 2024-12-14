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

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

public class NettyQuicConnectionContext internal constructor(

)

@RSocketTransportApi
internal class NettyQuicConnection(
    parentScope: CoroutineScope,
    private val channel: QuicChannel,
    private val initialStreamChannel: QuicStreamChannel,
) : MultiplexedRSocketConnection<NettyQuicConnectionContext> {
    override val coroutineContext: CoroutineContext
        get() = TODO("Not yet implemented")

    override val connectionContext: NettyQuicConnectionContext
        get() = TODO("Not yet implemented")

    override suspend fun sendConnectionFrame(frame: Buffer) {
        initialStreamChannel.writeAndFlushBuffer(frame).awaitFuture()
    }

    override suspend fun createStream(): MultiplexedRSocketConnection.Stream {
        return NettyQuicStream(channel.createStream(QuicStreamType.BIDIRECTIONAL, null).awaitFuture())
    }

    override fun startReceiving(inbound: MultiplexedRSocketConnection.Inbound) {
        channel.pipeline().addLast(NettyQuicConnectionInboundHandler(inbound))
        initialStreamChannel.pipeline().addLast(NettyQuicStreamConnectionInboundHandler(inbound))
    }
}

internal object NettyQuicConnectionClientInitializer : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.channel().config().isAutoRead = false
    }
}

@RSocketTransportApi
internal class NettyQuicConnectionServerInitializer(
    private val parentScope: CoroutineScope,
    private val inbound: RSocketServerInstance.Inbound<NettyQuicConnectionContext>,
) : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.channel().config().isAutoRead = false
        ctx.pipeline().addLast(NettyQuicConnectionFirstStreamHandler(parentScope, inbound))
    }
}

@RSocketTransportApi
private class NettyQuicConnectionInboundHandler(
    private val inbound: MultiplexedRSocketConnection.Inbound,
) : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.channel().config().isAutoRead = true
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        (msg as QuicStreamChannel).pipeline().addLast(NettyQuicStreamFirstFrameHandler(inbound))
    }
}

@RSocketTransportApi
private class NettyQuicConnectionFirstStreamHandler(
    private val parentScope: CoroutineScope,
    private val inbound: RSocketServerInstance.Inbound<NettyQuicConnectionContext>,
) : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.read() // need to read the first stream
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        // remove handler as it's not necessary anymore
        ctx.pipeline().remove(this)

        inbound.onConnection(
            NettyQuicConnection(
                parentScope = parentScope,
                channel = ctx.channel() as QuicChannel,
                initialStreamChannel = msg as QuicStreamChannel
            )
        )
    }
}

@RSocketTransportApi
private class NettyQuicStream(
    private val channel: QuicStreamChannel,
) : MultiplexedRSocketConnection.Stream {
    override val isClosedForSend: Boolean get() = channel.isOutputShutdown

    override suspend fun sendFrame(frame: Buffer) {
        channel.writeAndFlushBuffer(frame).awaitFuture()
    }

    override fun startReceiving(inbound: MultiplexedRSocketConnection.Stream.Inbound) {
        channel.pipeline().addLast(NettyQuicStreamInboundHandler(inbound))
    }

    override fun close() {
        channel.close()
    }
}

internal object NettyQuicStreamInitializer : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext): Unit = with(ctx.pipeline()) {
        ctx.channel().config().isAutoRead = false
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
    }
}

@RSocketTransportApi
private class NettyQuicStreamInboundHandler(
    private val inbound: MultiplexedRSocketConnection.Stream.Inbound,
) : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.channel().config().isAutoRead = true
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        inbound.onFrame((msg as ByteBuf).toBuffer())
    }

    // TODO: recheck
    override fun channelInactive(ctx: ChannelHandlerContext?) {
        inbound.onClose()
    }
}

@RSocketTransportApi
private class NettyQuicStreamConnectionInboundHandler(
    private val inbound: MultiplexedRSocketConnection.Inbound,
) : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.channel().config().isAutoRead = true
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        inbound.onConnectionFrame((msg as ByteBuf).toBuffer())
    }
}

@RSocketTransportApi
private class NettyQuicStreamFirstFrameHandler(
    private val inbound: MultiplexedRSocketConnection.Inbound,
) : ChannelInboundHandlerAdapter() {
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.read() // need to read the first frame
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        // remove handler as it's not necessary anymore
        ctx.pipeline().remove(this)

        inbound.onStream(
            frame = (msg as ByteBuf).toBuffer(),
            stream = NettyQuicStream(ctx.channel() as QuicStreamChannel)
        )
    }
}