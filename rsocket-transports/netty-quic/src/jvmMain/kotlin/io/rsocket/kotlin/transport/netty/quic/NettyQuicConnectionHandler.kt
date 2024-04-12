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

import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyQuicConnectionHandler(
    private val channel: QuicChannel,
    private val handler: RSocketConnectionHandler,
    scope: CoroutineScope,
    private val isClient: Boolean,
) : ChannelInboundHandlerAdapter() {
    private val inbound = Channel<RSocketMultiplexedConnection.Stream>(Channel.UNLIMITED)

    private val connectionJob = Job(scope.coroutineContext.job)
    private val streamsContext = scope.coroutineContext + SupervisorJob(connectionJob)

    private val handlerJob = scope.launch(connectionJob, start = CoroutineStart.LAZY) {
        try {
            handler.handleConnection(NettyQuicConnection(channel, inbound, streamsContext, isClient))
        } finally {
            inbound.cancel()
            withContext(NonCancellable) {
                streamsContext.job.cancelAndJoin()
                channel.close().awaitFuture()
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        handlerJob.start()
        connectionJob.complete()
        ctx.pipeline().addLast("rsocket-inbound", NettyQuicConnectionInboundHandler(inbound, streamsContext, isClient))

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

// TODO: implement support for isAutoRead=false to support `inbound` backpressure
@RSocketTransportApi
private class NettyQuicConnectionInboundHandler(
    private val inbound: SendChannel<RSocketMultiplexedConnection.Stream>,
    private val streamsContext: CoroutineContext,
    private val isClient: Boolean,
) : ChannelInboundHandlerAdapter() {
    // Note: QUIC streams could be received unordered, so f.e we could receive first stream with id 4 and then with id 0
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        msg as QuicStreamChannel
        val state = NettyQuicStreamState(null)
        if (inbound.trySend(state.wrapStream(msg)).isSuccess) {
            msg.pipeline().addLast(NettyQuicStreamInitializer(streamsContext, state, isClient))
        }
        ctx.fireChannelRead(msg)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt is ChannelInputShutdownEvent) {
            inbound.close()
        }
        super.userEventTriggered(ctx, evt)
    }
}

@RSocketTransportApi
private class NettyQuicConnection(
    private val channel: QuicChannel,
    private val inbound: ReceiveChannel<RSocketMultiplexedConnection.Stream>,
    private val streamsContext: CoroutineContext,
    private val isClient: Boolean,
) : RSocketMultiplexedConnection {
    private val startMarker = Job()

    // we need to `hack` only first stream created for client - stream where frames with streamId=0 will be sent
    private val first = AtomicBoolean(isClient)
    override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
        val startMarker = if (first.getAndSet(false)) {
            startMarker
        } else {
            startMarker.join()
            null
        }
        val state = NettyQuicStreamState(startMarker)
        val stream = try {
            channel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                NettyQuicStreamInitializer(streamsContext, state, isClient)
            ).awaitFuture()
        } catch (cause: Throwable) {
            state.closeMarker.complete()
            throw cause
        }

        return state.wrapStream(stream)
    }

    override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
        return inbound.receiveCatching().getOrNull()
    }
}
