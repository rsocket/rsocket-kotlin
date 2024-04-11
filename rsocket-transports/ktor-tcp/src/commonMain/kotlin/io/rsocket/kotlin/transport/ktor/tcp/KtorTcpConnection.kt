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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

@RSocketTransportApi
internal suspend fun RSocketConnectionHandler.handleKtorTcpConnection(socket: Socket): Unit = coroutineScope {
    val outboundQueue = PrioritizationFrameQueue(Channel.BUFFERED)
    val inbound = channelForCloseable<ByteReadPacket>(Channel.BUFFERED)

    val readerJob = launch {
        val input = socket.openReadChannel()
        try {
            while (true) inbound.send(input.readFrame() ?: break)
            input.cancel(null)
        } catch (cause: Throwable) {
            input.cancel(cause)
            throw cause
        }
    }.onCompletion { inbound.cancel() }

    val writerJob = launch {
        val output = socket.openWriteChannel()
        try {
            while (true) {
                // we write all available frames here, and only after it flush
                // in this case, if there are several buffered frames we can send them in one go
                // avoiding unnecessary flushes
                output.writeFrame(outboundQueue.dequeueFrame() ?: break)
                while (true) output.writeFrame(outboundQueue.tryDequeueFrame() ?: break)
                output.flush()
            }
            output.close(null)
        } catch (cause: Throwable) {
            output.close(cause)
            throw cause
        }
    }.onCompletion { outboundQueue.cancel() }

    try {
        handleConnection(KtorTcpConnection(outboundQueue, inbound))
    } finally {
        readerJob.cancel()
        outboundQueue.close() // will cause `writerJob` completion
        // even if it was cancelled, we still need to close socket and await it closure
        withContext(NonCancellable) {
            // await completion of read/write and then close socket
            readerJob.join()
            writerJob.join()
            // close socket
            socket.close()
            socket.socketContext.join()
        }
    }
}

@RSocketTransportApi
private class KtorTcpConnection(
    private val outboundQueue: PrioritizationFrameQueue,
    private val inbound: ReceiveChannel<ByteReadPacket>,
) : RSocketSequentialConnection {
    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend
    override suspend fun sendFrame(streamId: Int, frame: ByteReadPacket) {
        return outboundQueue.enqueueFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): ByteReadPacket? {
        return inbound.receiveCatching().getOrNull()
    }
}

private suspend fun ByteWriteChannel.writeFrame(frame: ByteReadPacket) {
    val packet = buildPacket {
        writeInt24(frame.remaining.toInt())
        writePacket(frame)
    }
    try {
        writePacket(packet)
    } catch (cause: Throwable) {
        packet.close()
        throw cause
    }
}

private suspend fun ByteReadChannel.readFrame(): ByteReadPacket? {
    val lengthPacket = readRemaining(3)
    if (lengthPacket.remaining == 0L) return null
    return readPacket(lengthPacket.readInt24())
}
