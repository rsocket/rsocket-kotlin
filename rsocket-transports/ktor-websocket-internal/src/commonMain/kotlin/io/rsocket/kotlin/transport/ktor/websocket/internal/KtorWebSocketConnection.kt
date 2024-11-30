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

package io.rsocket.kotlin.transport.ktor.websocket.internal

import io.ktor.websocket.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*

@RSocketTransportApi
public suspend fun RSocketConnectionHandler.handleKtorWebSocketConnection(webSocketSession: WebSocketSession): Unit = coroutineScope {
    val outboundQueue = PrioritizationFrameQueue(Channel.BUFFERED)

    val senderJob = launch {
        while (true) webSocketSession.send(outboundQueue.dequeueFrame()?.readByteArray() ?: break)
    }.onCompletion { outboundQueue.cancel() }

    try {
        handleConnection(KtorWebSocketConnection(outboundQueue, webSocketSession.incoming))
    } finally {
        webSocketSession.incoming.cancel()
        outboundQueue.close()
        withContext(NonCancellable) {
            senderJob.join() // await all frames sent
            webSocketSession.close()
            webSocketSession.coroutineContext.job.join()
        }
    }
}

@RSocketTransportApi
private class KtorWebSocketConnection(
    private val outboundQueue: PrioritizationFrameQueue,
    private val inbound: ReceiveChannel<Frame>,
) : RSocketSequentialConnection {
    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

    override suspend fun sendFrame(streamId: Int, frame: Buffer) {
        return outboundQueue.enqueueFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): Buffer? {
        val frame = inbound.receiveCatching().getOrNull() ?: return null
        return Buffer().apply { write(frame.data) }
    }
}
