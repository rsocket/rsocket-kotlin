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

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.*
import io.netty.handler.ssl.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.io.*
import java.net.*
import kotlin.coroutines.*

public class NettyTcpConnectionContext(
    public val localAddress: SocketAddress,
    public val remoteAddress: SocketAddress,
)

@RSocketTransportApi
internal class NettyTcpConnection(
    parentScope: CoroutineScope,
    private val channel: DuplexChannel,
) : SequentialRSocketConnection<NettyTcpConnectionContext> {
    private val dispatcher = channel.eventLoop().asCoroutineDispatcher()
    private val outboundQueue = PrioritizationFrameQueue()
    private var outboundJob: Job? = null

    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

    override val connectionContext: NettyTcpConnectionContext = NettyTcpConnectionContext(
        localAddress = channel.localAddress(),
        remoteAddress = channel.remoteAddress()
    )
    override val coroutineContext: CoroutineContext = parentScope.coroutineContext + parentScope.launch(dispatcher) {
        try {
            awaitCancellation()
        } catch (cause: Throwable) {
            withContext(NonCancellable) {
                // will cause `writerJob` completion
                outboundQueue.close()
                outboundJob?.join()

                //channel.shutdown().awaitFuture()
                channel.close().awaitFuture()
            }
        }
    }

    init {
        outboundJob = launch(dispatcher) {
            try {
                while (true) {
                    // we write all available frames here, and only after it flush
                    // in this case, if there are several buffered frames we can send them in one go
                    // avoiding unnecessary flushes
                    // TODO: could be optimized to avoid allocation of not-needed promises
                    var lastWriteFuture = channel.writeBuffer(outboundQueue.dequeueFrame() ?: break)
                    while (true) lastWriteFuture = channel.writeBuffer(outboundQueue.tryDequeueFrame() ?: break)
                    channel.flush()
                    // await writing to respect transport backpressure
                    lastWriteFuture.awaitFuture()
                }
            } finally {
                outboundQueue.cancel()
            }
        }
    }

    override suspend fun sendConnectionFrame(frame: Buffer) {
        return outboundQueue.enqueuePriorityFrame(frame)
    }

    override suspend fun sendStreamFrame(frame: Buffer) {
        return outboundQueue.enqueueNormalFrame(frame)
    }

    override fun startReceiving(inbound: SequentialRSocketConnection.Inbound) {
        channel.pipeline().addLast(NettyTcpConnectionInboundHandler(inbound))
        channel.config().isAutoRead = true
    }
}

@RSocketTransportApi
private class NettyTcpConnectionInboundHandler(
    private val inbound: SequentialRSocketConnection.Inbound,
) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        inbound.onFrame((msg as ByteBuf).toBuffer())
    }
}

internal class NettyTcpConnectionInitializer(
    private val sslContext: SslContext?,
) : ChannelInitializer<DuplexChannel>() {
    override fun initChannel(channel: DuplexChannel): Unit = with(channel.pipeline()) {
        channel.config().isAutoRead = false

        if (sslContext != null) {
            addLast("ssl", sslContext.newHandler(channel.alloc()))
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
    }
}

@RSocketTransportApi
internal class NettyTcpConnectionServerInitializer(
    private val parentScope: CoroutineScope,
    private val inbound: RSocketServerInstance.Inbound<NettyTcpConnectionContext>,
    private val childHandler: ChannelHandler,
) : ChannelInitializer<DuplexChannel>() {
    override fun initChannel(ch: DuplexChannel) {
        ch.pipeline().addLast(childHandler)
        inbound.onConnection(NettyTcpConnection(parentScope, ch))
    }
}
