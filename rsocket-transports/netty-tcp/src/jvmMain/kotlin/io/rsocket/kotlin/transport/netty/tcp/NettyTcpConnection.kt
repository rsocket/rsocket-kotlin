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

package io.rsocket.kotlin.transport.netty.tcp

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.util.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import io.rsocket.kotlin.transport.netty.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class NettyTcpConnection(
    parentContext: CoroutineContext,
    private val channel: DuplexChannel,
) : RSocketSequentialConnection, ChannelInboundHandlerAdapter() {

    private val outboundQueue = PrioritizationFrameQueue(Channel.BUFFERED)
    private val inbound = bufferChannel(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = parentContext.childContext() + channel.eventLoop().asCoroutineDispatcher()

    init {
        @OptIn(DelicateCoroutinesApi::class)
        launch(start = CoroutineStart.ATOMIC) {
            val outboundJob = launch {
                nonCancellable {
                    try {
                        while (true) {
                            // we write all available frames here, and only after it flush
                            // in this case, if there are several buffered frames we can send them in one go
                            // avoiding unnecessary flushes
                            channel.writeBuffer(outboundQueue.dequeueFrame() ?: break)
                            while (true) channel.writeBuffer(outboundQueue.tryDequeueFrame() ?: break)
                            channel.flush()
                        }
                    } finally {
                        outboundQueue.cancel()
                        channel.shutdownOutput(channel.voidPromise())
                    }
                }
            }
            try {
                awaitCancellation()
            } finally {
                nonCancellable {
                    outboundQueue.close()
                    inbound.cancel()
                    channel.shutdownInput(channel.voidPromise())
                    outboundJob.join()
                    channel.close().awaitFuture()
                }
            }
        }
    }

    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

    // TODO: check channel state
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buffer = (msg as ByteBuf).toBuffer()
        if (inbound.trySend(buffer).isFailure) buffer.clear()
    }

    override suspend fun sendFrame(streamId: Int, frame: Buffer) {
        return outboundQueue.enqueueFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): Buffer? {
        inbound.tryReceive().onSuccess { return it }
        channel.read()
        return inbound.receiveCatching().getOrNull()
    }

    companion object {
        val ATTRIBUTE: AttributeKey<RSocketConnection> = AttributeKey.newInstance<RSocketConnection>("rsocket-tcp-connection")
    }
}
