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
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*

@RSocketTransportApi
public class KtorWebSocketConnection<T>(
    parentScope: CoroutineScope,
    private val webSocketSession: WebSocketSession,
    override val connectionContext: T,
) : SequentialRSocketConnection<T> {
    private val outboundQueue = PrioritizationFrameQueue()
    private var inboundJob: Job? = null
    private var outboundJob: Job? = null

    override val coroutineContext: CoroutineContext = parentScope.coroutineContext + parentScope.launch(Dispatchers.Unconfined) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                // await inbound completion
                inboundJob?.cancel()
                outboundQueue.close() // will cause `writerJob` completion
                // await completion of read/write jobs
                inboundJob?.join()
                outboundJob?.join()
                // await socket completion
                webSocketSession.close()
                webSocketSession.coroutineContext.job.join()
            }
        }
    }

    override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

    init {
        outboundJob = webSocketSession.launch(Dispatchers.Unconfined) {
            try {
                while (true) {
                    webSocketSession.send(outboundQueue.dequeueFrame()?.readByteArray() ?: break)
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
        inboundJob = webSocketSession.launch(Dispatchers.Unconfined) {
            webSocketSession.incoming.consumeEach { frame ->
                inbound.onFrame(Buffer().apply { write(frame.data) })
            }
        }
    }
}
