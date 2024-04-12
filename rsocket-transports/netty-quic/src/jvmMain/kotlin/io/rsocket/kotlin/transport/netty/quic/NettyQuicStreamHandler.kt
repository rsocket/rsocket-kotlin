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

import io.ktor.utils.io.core.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel

// TODO: first stream is a hack to initiate first stream because of buffering
//  quic streams could be received unordered by server, so f.e we could receive first stream with id 4 and then with id 0
//  for this, we disable buffering for first client stream, so that first frame will be sent first
//  this will affect performance for this stream, so we need to do something else here.
@RSocketTransportApi
internal class NettyQuicStreamState(val startMarker: CompletableJob?) {
    val closeMarker: CompletableJob = Job()
    val outbound = channelForCloseable<ByteReadPacket>(Channel.BUFFERED)
    val inbound = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)

    fun wrapStream(stream: QuicStreamChannel): RSocketMultiplexedConnection.Stream =
        NettyQuicStream(stream, outbound, inbound, closeMarker)
}

@RSocketTransportApi
internal class NettyQuicStreamHandler(
    private val channel: QuicStreamChannel,
    scope: CoroutineScope,
    private val state: NettyQuicStreamState,
    private val isClient: Boolean,
) : ChannelInboundHandlerAdapter() {
    private val handlerJob = scope.launch(start = CoroutineStart.LAZY) {
        val outbound = state.outbound

        val writerJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                while (true) {
                    // we write all available frames here, and only after it flush
                    // in this case, if there are several buffered frames we can send them in one go
                    // avoiding unnecessary flushes
                    // TODO: could be optimized to avoid allocation of not-needed promises

                    var lastWriteFuture = channel.write(outbound.receiveCatching().getOrNull()?.toByteBuf() ?: break)
                    while (true) lastWriteFuture = channel.write(outbound.tryReceive().getOrNull()?.toByteBuf() ?: break)
                    //println("FLUSH: $isClient: ${channel.streamId()}")
                    channel.flush()
                    // await writing to respect transport backpressure
                    lastWriteFuture.awaitFuture()
                    state.startMarker?.complete()
                }
            } finally {
                withContext(NonCancellable) {
                    channel.shutdownOutput().awaitFuture()
                }
            }
        }.onCompletion { outbound.cancel() }

        try {
            state.closeMarker.join()
        } finally {
            outbound.close() // will cause `writerJob` completion
            // no more reading
            state.inbound.cancel()
            withContext(NonCancellable) {
                writerJob.join()
                channel.close().awaitFuture()
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        handlerJob.start()
        ctx.pipeline().addLast("rsocket-inbound", NettyQuicStreamInboundHandler(state.inbound))

        ctx.fireChannelActive()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        handlerJob.cancel("Channel is not active")

        ctx.fireChannelInactive()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        handlerJob.cancel("exceptionCaught", cause)
    }
}

private class NettyQuicStreamInboundHandler(
    private val inbound: SendChannel<ByteReadPacket>,
) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        msg as ByteBuf
        try {
            val frame = msg.toByteReadPacket()
            if (inbound.trySend(frame).isFailure) {
                frame.close()
            }
        } finally {
            msg.release()
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt is ChannelInputShutdownEvent) {
            inbound.close()
        }
        super.userEventTriggered(ctx, evt)
    }
}

@RSocketTransportApi
private class NettyQuicStream(
    // for priority
    private val stream: QuicStreamChannel,
    private val outbound: SendChannel<ByteReadPacket>,
    private val inbound: ReceiveChannel<ByteReadPacket>,
    private val closeMarker: CompletableJob,
) : RSocketMultiplexedConnection.Stream {

    @OptIn(DelicateCoroutinesApi::class)
    override val isClosedForSend: Boolean get() = outbound.isClosedForSend

    override fun setSendPriority(priority: Int) {
        stream.updatePriority(QuicStreamPriority(priority, false))
    }

    override suspend fun sendFrame(frame: ByteReadPacket) {
        outbound.send(frame)
    }

    override suspend fun receiveFrame(): ByteReadPacket? {
        return inbound.receiveCatching().getOrNull()
    }

    override fun close() {
        closeMarker.complete()
    }
}
