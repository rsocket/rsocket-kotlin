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
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*

public class KtorTcpConnectionContext internal constructor(
    public val localAddress: SocketAddress,
    public val remoteAddress: SocketAddress,
)

@RSocketTransportApi
internal suspend fun RSocketConnectionInbound.handleKtorTcpConnection(socket: Socket): Unit = coroutineScope {
    val outboundQueue = PrioritizationFrameQueue(Channel.BUFFERED)
    val inbound = bufferChannel(Channel.BUFFERED)

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
internal class KtorTcpConnection(
    parentContext: CoroutineContext,
    private val socket: Socket,
) : SequentialRSocketConnection {
    private val connectionJob = Job(parentContext[Job])
    override val coroutineContext: CoroutineContext = parentContext + connectionJob

    private val outboundQueue = PrioritizationFrameQueue()
    private var readerJob: Job? = null

    override val isClosedForSend: Boolean
        get() = outboundQueue.isClosedForSend

    init {
        launch(Dispatchers.Unconfined) {
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
                output.flushAndClose()
            } catch (cause: Throwable) {
                output.cancel(cause)
                throw cause
            }
        }
    }

    override suspend fun sendConnectionFrame(frame: Buffer) {
        return outboundQueue.enqueuePriorityFrame(frame)
    }

    override suspend fun sendStreamFrame(frame: Buffer) {
        return outboundQueue.enqueueNormalFrame(frame)
    }

    override fun close(cause: Throwable?) {
        outboundQueue.close()
        readerJob?.cancel("Connection closed", cause)
        // connectionJob.cancel("Connection closed", cause)
    }

    override fun startReceiving(inbound: SequentialRSocketConnection.Inbound) {
        readerJob = launch(Dispatchers.Unconfined) {
            val input = socket.openReadChannel()
            try {
                while (true) inbound.onFrame(input.readFrame() ?: break)
                input.cancel(null)
                inbound.onClose(null)
            } catch (cause: Throwable) {
                input.cancel(cause)
                inbound.onClose(cause)
                throw cause
            }
        }
    }

    @OptIn(InternalAPI::class)
    private fun ByteWriteChannel.writeFrame(frame: Buffer) {
        writeBuffer.writeInt24(frame.size.toInt())
        writeBuffer.transferFrom(frame)
    }

    @OptIn(InternalAPI::class)
    private suspend fun ByteReadChannel.readFrame(): Buffer? {
        while (availableForRead < 3 && awaitContent(3)) yield()
        if (availableForRead == 0) return null

        val length = readBuffer.readInt24()
        return readBuffer(length).also {
            it.require(length.toLong())
        }
    }
}
