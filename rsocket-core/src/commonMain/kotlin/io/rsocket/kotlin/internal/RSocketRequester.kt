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

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.handler.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalStreamsApi::class)
internal class RSocketRequester(
    connectionJob: Job,
    private val prioritizer: Prioritizer,
    private val streamsStorage: StreamsStorage,
    private val requestScope: CoroutineScope
) : RSocket {
    override val job: Job = connectionJob

    override suspend fun metadataPush(metadata: ByteReadPacket) {
        ensureActiveOrRelease(metadata)
        metadata.closeOnError {
            prioritizer.send(MetadataPushFrame(metadata))
        }
    }

    override suspend fun fireAndForget(payload: Payload) {
        ensureActiveOrRelease(payload)

        val streamId = streamsStorage.nextId()
        try {
            prioritizer.send(RequestFireAndForgetFrame(streamId, payload))
        } catch (cause: Throwable) {
            payload.release()
            if (job.isActive) prioritizer.send(CancelFrame(streamId)) //if cancelled during fragmentation
            throw cause
        }
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        ensureActiveOrRelease(payload)

        val streamId = streamsStorage.nextId()

        val deferred = CompletableDeferred<Payload>()
        val handler = RequesterRequestResponseFrameHandler(streamId, streamsStorage, deferred)
        streamsStorage.save(streamId, handler)

        return handler.receiveOrCancel(streamId, payload) {
            prioritizer.send(RequestResponseFrame(streamId, payload))
            deferred.await()
        }
    }

    override fun requestStream(payload: Payload): Flow<Payload> = requestFlow { strategy, initialRequest ->
        ensureActiveOrRelease(payload)

        val streamId = streamsStorage.nextId()

        val channel = SafeChannel<Payload>(Channel.UNLIMITED)
        val handler = RequesterRequestStreamFrameHandler(streamId, streamsStorage, channel)
        streamsStorage.save(streamId, handler)

        handler.receiveOrCancel(streamId, payload) {
            prioritizer.send(RequestStreamFrame(streamId, initialRequest, payload))
            emitAllWithRequestN(channel, strategy) { prioritizer.send(RequestNFrame(streamId, it)) }
        }
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = requestFlow { strategy, initialRequest ->
        ensureActiveOrRelease(initPayload)

        val streamId = streamsStorage.nextId()

        val channel = SafeChannel<Payload>(Channel.UNLIMITED)
        val limiter = Limiter(0)
        val sender = Job(requestScope.coroutineContext.job)
        val handler = RequesterRequestChannelFrameHandler(streamId, streamsStorage, limiter, sender, channel)
        streamsStorage.save(streamId, handler)

        handler.receiveOrCancel(streamId, initPayload) {
            prioritizer.send(RequestChannelFrame(streamId, initialRequest, initPayload))
            //TODO lazy?
            requestScope.launch(sender) {
                handler.sendOrFail(streamId) {
                    payloads.collectLimiting(limiter) { prioritizer.send(NextPayloadFrame(streamId, it)) }
                    prioritizer.send(CompletePayloadFrame(streamId))
                }
            }
            emitAllWithRequestN(channel, strategy) { prioritizer.send(RequestNFrame(streamId, it)) }
        }
    }

    private suspend inline fun SendFrameHandler.sendOrFail(id: Int, block: () -> Unit) {
        try {
            block()
            onSendComplete()
        } catch (cause: Throwable) {
            val isFailed = onSendFailed(cause)
            if (job.isActive && isFailed) prioritizer.send(ErrorFrame(id, cause))
            throw cause
        }
    }

    private suspend inline fun <T> ReceiveFrameHandler.receiveOrCancel(id: Int, payload: Payload, block: () -> T): T {
        try {
            val result = block()
            onReceiveComplete()
            return result
        } catch (cause: Throwable) {
            payload.release()
            val isCancelled = onReceiveCancelled(cause)
            if (job.isActive && isCancelled) prioritizer.send(CancelFrame(id))
            throw cause
        }
    }

    private fun ensureActiveOrRelease(closeable: Closeable) {
        if (job.isActive) return
        closeable.close()
        job.ensureActive()
    }
}
