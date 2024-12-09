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
import kotlin.coroutines.*

@RSocketTransportApi
public suspend fun RSocketConnectionInbound.handleKtorWebSocketConnection(webSocketSession: WebSocketSession): Unit = coroutineScope {
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
    private val webSocketSession: WebSocketSession,
    private val inbound: ReceiveChannel<Frame>,
) : SequentialRSocketConnection {
    private val outboundQueue = PrioritizationFrameQueue()

    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

    override val coroutineContext: CoroutineContext
        get() = TODO("Not yet implemented")

    init {
        val senderJob = launch {
            while (true) webSocketSession.send(outboundQueue.dequeueFrame()?.readByteArray() ?: break)
        }.onCompletion { outboundQueue.cancel() }
    }

    override suspend fun sendConnectionFrame(frame: Buffer) {
        return outboundQueue.enqueuePriorityFrame(frame)
    }

    override suspend fun sendStreamFrame(frame: Buffer) {
        return outboundQueue.enqueueNormalFrame(frame)
    }

    override fun startReceiving(inbound: SequentialRSocketConnection.Inbound) {
        launch(Dispatchers.Unconfined) {
            try {
                while (true) {
                    // TODO: recheck
                    val frame = webSocketSession.incoming.receiveCatching().getOrNull() ?: break
                    inbound.onFrame(Buffer().apply { write(frame.data) })
                }
                webSocketSession.incoming.cancel()
                inbound.onClose(null)
            } catch (cause: Throwable) {
                webSocketSession.incoming.cancel(CancellationException("", cause))
                inbound.onClose(cause)
                throw cause
            }
        }
    }

    override fun close(cause: Throwable?) {
        TODO("Not yet implemented")
    }
}
