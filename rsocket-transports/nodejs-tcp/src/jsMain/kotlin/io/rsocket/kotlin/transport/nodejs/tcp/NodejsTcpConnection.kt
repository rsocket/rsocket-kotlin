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

package io.rsocket.kotlin.transport.nodejs.tcp

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*

@RSocketTransportApi
internal suspend fun RSocketConnectionHandler.handleNodejsTcpConnection(socket: Socket): Unit = coroutineScope {
    val outboundQueue = PrioritizationFrameQueue(Channel.BUFFERED)
    val inbound = bufferChannel(Channel.UNLIMITED)

    val closed = CompletableDeferred<Unit>()
    val frameAssembler = FrameWithLengthAssembler { inbound.trySend(it) }
    socket.on(
        onData = frameAssembler::write,
        onError = { closed.completeExceptionally(it) },
        onClose = {
            frameAssembler.close()
            if (!it) closed.complete(Unit)
        }
    )

    val writerJob = launch {
        while (true) socket.writeFrame(outboundQueue.dequeueFrame() ?: break)
    }.onCompletion { outboundQueue.cancel() }

    try {
        handleConnection(NodejsTcpConnection(outboundQueue, inbound))
    } finally {
        inbound.cancel()
        outboundQueue.close() // will cause `writerJob` completion
        // even if it was cancelled, we still need to close socket and await it closure
        withContext(NonCancellable) {
            writerJob.join()
            // close socket
            socket.destroy()
            closed.join()
        }
    }
}

@RSocketTransportApi
private class NodejsTcpConnection(
    private val outboundQueue: PrioritizationFrameQueue,
    private val inbound: ReceiveChannel<Buffer>,
) : RSocketSequentialConnection {
    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend
    override suspend fun sendFrame(streamId: Int, frame: Buffer) {
        return outboundQueue.enqueueFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): Buffer? {
        return inbound.receiveCatching().getOrNull()
    }
}
