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

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.*
import io.netty.incubator.codec.quic.*
import io.netty.util.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyQuicStream(
    parentContext: CoroutineContext,
    private val channel: QuicStreamChannel,
) : RSocketMultiplexedConnection.Stream, ChannelInboundHandlerAdapter() {

    private val outbound = bufferChannel(Channel.BUFFERED)
    private val inbound = bufferChannel(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = parentContext.childContext() + channel.eventLoop().asCoroutineDispatcher()

    init {
        @OptIn(DelicateCoroutinesApi::class)
        launch(start = CoroutineStart.UNDISPATCHED) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                nonCancellable {
                    try {
                        while (true) {
                            writeAndFlushBuffer(outbound.receiveCatching().getOrNull() ?: break)
                        }
                    } finally {
                        outbound.cancel()
                        channel.shutdownOutput().awaitFuture()
                    }
                }
            }
            try {
                awaitCancellation()
            } finally {
                outbound.close()
                inbound.cancel()
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        cancel("Channel is not active")
        ctx.fireChannelInactive()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        cancel("exceptionCaught", cause)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        if (evt === ChannelInputShutdownEvent.INSTANCE) inbound.close()
        super.userEventTriggered(ctx, evt)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buffer = (msg as ByteBuf).toBuffer()
        if (inbound.trySend(buffer).isFailure) buffer.clear()
    }

    override fun setSendPriority(priority: Int) {
        channel.updatePriority(QuicStreamPriority(priority, false))
    }

    override suspend fun sendFrame(frame: Buffer) {
        outbound.send(frame)
    }

    override suspend fun receiveFrame(): Buffer? {
        return inbound.receiveCatching().getOrNull()
    }

    private suspend fun writeAndFlushBuffer(buffer: Buffer) {
        channel.writeAndFlush(buffer.toByteBuf(channel.alloc())).awaitFuture()
    }

    companion object {
        val ATTRIBUTE: AttributeKey<NettyQuicStream> = AttributeKey.newInstance<NettyQuicStream>("rsocket-quic-stream")
    }
}

@RSocketTransportApi
internal object NettyQuicStreamInitializer : ChannelInitializer<QuicStreamChannel>() {
    override fun initChannel(channel: QuicStreamChannel) {
        channel.pipeline().addLast(
            "rsocket-length-encoder",
            LengthFieldPrepender(
                /* lengthFieldLength = */ 3
            )
        )
        channel.pipeline().addLast(
            "rsocket-length-decoder",
            LengthFieldBasedFrameDecoder(
                /* maxFrameLength = */ Int.MAX_VALUE,
                /* lengthFieldOffset = */ 0,
                /* lengthFieldLength = */ 3,
                /* lengthAdjustment = */ 0,
                /* initialBytesToStrip = */ 3
            )
        )

        channel.parent()
            .attr(NettyQuicConnection.ATTRIBUTE).get()
            .initStreamChannel(channel)
    }
}
