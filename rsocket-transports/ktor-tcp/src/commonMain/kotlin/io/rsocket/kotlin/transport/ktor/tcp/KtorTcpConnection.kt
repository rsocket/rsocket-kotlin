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
import kotlinx.io.*
import kotlin.coroutines.*

public class KtorTcpConnectionContext internal constructor(
    public val localAddress: SocketAddress,
    public val remoteAddress: SocketAddress,
)

@RSocketTransportApi
internal class KtorTcpConnection(
    parentScope: CoroutineScope,
    private val socket: Socket,
) : SequentialRSocketConnection<KtorTcpConnectionContext> {
    private val outboundQueue = PrioritizationFrameQueue()
    private var inboundJob: Job? = null
    private var outboundJob: Job? = null

    override val connectionContext: KtorTcpConnectionContext = KtorTcpConnectionContext(socket.localAddress, socket.remoteAddress)
    override val coroutineContext: CoroutineContext = parentScope.coroutineContext + parentScope.launch(Dispatchers.Unconfined) {
        try {
            // await connection completion
            awaitCancellation()
        } finally {
            // even if it was cancelled, we still need to close socket and await it closure
            withContext(NonCancellable) {
                // await inbound completion
                inboundJob?.cancel()
                outboundQueue.close() // will cause `writerJob` completion
                // await completion of read/write jobs
                inboundJob?.join()
                outboundJob?.join()
                // await socket completion
                socket.close()
                socket.socketContext.join()
            }
        }
    }
    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

    init {
        outboundJob = socket.launch(Dispatchers.Unconfined) {
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
            } finally {
                outboundQueue.cancel() // cleanup frames
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
        inboundJob = socket.launch(Dispatchers.Unconfined) {
            val input = socket.openReadChannel()
            try {
                while (true) inbound.onFrame(input.readFrame() ?: break)
                input.cancel(null)
            } catch (cause: Throwable) {
                input.cancel(cause)
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
