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

import io.netty.channel.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyQuicConnection(
    parentContext: CoroutineContext,
    private val channel: QuicChannel,
) : RSocketMultiplexedConnection, ChannelInboundHandlerAdapter() {
    private val inboundStreams = Channel<RSocketMultiplexedConnection.Stream>(Channel.UNLIMITED)
    override val coroutineContext: CoroutineContext = parentContext.childContext() + channel.eventLoop().asCoroutineDispatcher()
    private val streamsScope = CoroutineScope(coroutineContext.supervisorContext())

    init {
        @OptIn(DelicateCoroutinesApi::class)
        launch(start = CoroutineStart.ATOMIC) {
            try {
                awaitCancellation()
            } finally {
                nonCancellable {
                    inboundStreams.cancel()
                    // stop streams first
                    streamsScope.coroutineContext.job.cancelAndJoin()
                    channel.close().awaitFuture()
                }
            }
        }
    }

    fun initStreamChannel(streamChannel: QuicStreamChannel) {
        val stream = NettyQuicStream(streamsScope, streamChannel)
        streamChannel.attr(ATTRIBUTE_STREAM).set(stream)
        streamChannel.pipeline().addLast("rsocket-quic-stream", stream)

        if (streamChannel.isLocalCreated) return

        if (inboundStreams.trySend(stream).isFailure) stream.close()
    }

//    override fun channelActive(ctx: ChannelHandlerContext) {
//        handlerJob.start()
//        connectionJob.complete()
//        ctx.pipeline().addLast("rsocket-inbound", NettyQuicConnectionInboundHandler(inbound, streamsContext, isClient))
//
//        ctx.fireChannelActive()
//    }
//
//    override fun channelInactive(ctx: ChannelHandlerContext) {
//        handlerJob.cancel("Channel is not active")
//
//        ctx.fireChannelInactive()
//    }
//
//    @Suppress("OVERRIDE_DEPRECATION")
//    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
//        handlerJob.cancel("exceptionCaught", cause)
//    }
//    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
//        if (evt is ChannelInputShutdownEvent) {
//            inbound.close()
//        }
//        super.userEventTriggered(ctx, evt)
//    }

    override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
        val streamChannel = channel.createStream(QuicStreamType.BIDIRECTIONAL, NettyQuicStreamInitializer).awaitFuture()
        return streamChannel.attr(ATTRIBUTE_STREAM).get()
    }

    override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
        return inboundStreams.receiveCatching().getOrNull()
    }
}

@RSocketTransportApi
internal object NettyQuicConnectionInitializer : ChannelInitializer<QuicChannel>() {
    override fun initChannel(channel: QuicChannel) {
        val connection = NettyQuicConnection(
            parentContext = channel.parent().attr(ATTRIBUTE_TRANSPORT_CONTEXT).get(),
            channel = channel
        )
        channel.attr(ATTRIBUTE_CONNECTION).set(connection)

        //addLast(LoggingHandler(if (isClient) "CLIENT" else "SERVER"))
        channel.pipeline().addLast(
            "rsocket-connection",
            connection
        )

        // initialize is run only for server
        channel.parent()
            .attr(ATTRIBUTE_CONNECTION_INITIALIZER).get()
            ?.launchInitializer(connection)
    }
}
