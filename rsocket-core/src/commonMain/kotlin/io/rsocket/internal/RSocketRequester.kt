/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.internal

import io.rsocket.*
import io.rsocket.flow.*
import io.rsocket.frame.*
import io.rsocket.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class RSocketRequester(
    state: RSocketState,
    private val streamId: StreamId
) : RSocket, RSocketState by state {
    private fun nextStreamId(): Int = streamId.next(streamIds)

    override fun metadataPush(metadata: ByteArray) {
        checkAvailable()
        sendPrioritized(MetadataPushFrame(metadata))
    }

    override fun fireAndForget(payload: Payload) {
        checkAvailable()
        send(RequestFireAndForgetFrame(nextStreamId(), payload))
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        checkAvailable()
        val streamId = nextStreamId()
        val receiver = receiver(streamId)
        send(RequestResponseFrame(streamId, payload))
        return receiveOne(streamId, receiver)
    }

    override fun requestStream(payload: Payload): RequestingFlow<Payload> = requestingFlow {
        checkAvailable()
        val streamId = nextStreamId()
        val receiver = receiver(streamId)
        send(RequestStreamFrame(streamId, initialRequest, payload))
        emitAll(streamId, receiver)
    }

    override fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> = requestingFlow {
        checkAvailable()
        val streamId = nextStreamId()
        val request = payloads.sendLimiting(streamId, 1)
        val firstPayload = request.firstOrNull() ?: return@requestingFlow
        val receiver = receiver(streamId)
        send(RequestChannelFrame(streamId, initialRequest, firstPayload))
        launchCancelable(streamId) {
            sendStream(streamId, request)
        }.invokeOnCompletion {
            if (it != null && it !is CancellationException) receiver.cancelConsumed(it)
        }
        try {
            emitAll(streamId, receiver)
        } catch (e: Throwable) {
            request.cancelConsumed(e)
            throw e
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun ReceiveChannel<Payload>.firstOrNull(): Payload? {
        try {
            val value = receiveOrNull()
            if (value == null) cancel()
            return value
        } catch (e: Throwable) {
            cancelConsumed(e)
            throw e
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun checkAvailable() {
        if (isActive) return
        val error = job.getCancellationException()
        throw error.cause ?: error
    }

}
