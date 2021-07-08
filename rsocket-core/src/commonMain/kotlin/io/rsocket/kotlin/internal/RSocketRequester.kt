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
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalStreamsApi::class)
internal class RSocketRequester(
    private val state: RSocketState,
    private val streamId: StreamId,
) : RSocket {
    override val job: Job get() = state.job

    override suspend fun metadataPush(metadata: ByteReadPacket): Unit = metadata.closeOnError {
        job.ensureActive()
        state.sendPrioritized(MetadataPushFrame(metadata))
    }

    override suspend fun fireAndForget(payload: Payload): Unit = payload.closeOnError {
        val streamId = createStream()
        state.send(RequestFireAndForgetFrame(streamId, payload))
    }

    override suspend fun requestResponse(payload: Payload): Payload = with(state) {
        payload.closeOnError {
            val streamId = createStream()
            val receiver = createReceiverFor(streamId)
            send(RequestResponseFrame(streamId, payload))
            consumeReceiverFor(streamId) {
                receiver.receive().payload //TODO fragmentation
            }
        }
    }

    override fun requestStream(payload: Payload): Flow<Payload> = with(state) {
        requestFlow { strategy, initialRequest ->
            payload.closeOnError {
                val streamId = createStream()
                val receiver = createReceiverFor(streamId)
                send(RequestStreamFrame(streamId, initialRequest, payload))
                collectStream(streamId, receiver, strategy, this)
            }
        }
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = with(state) {
        requestFlow { strategy, initialRequest ->
            initPayload.closeOnError {
                val streamId = createStream()
                val receiver = createReceiverFor(streamId)
                val request = launchCancelable(streamId) {
                    payloads.collectLimiting(streamId, 0) {
                        send(RequestChannelFrame(streamId, initialRequest, initPayload))
                    }
                }

                request.invokeOnCompletion {
                    if (it != null && it !is CancellationException) receiver.cancelConsumed(it)
                }
                try {
                    collectStream(streamId, receiver, strategy, this)
                } catch (e: Throwable) {
                    if (e is CancellationException) request.cancel(e)
                    else request.cancel("Receiver failed", e)
                    throw e
                }
            }
        }
    }

    fun createStream(): Int {
        job.ensureActive()
        return nextStreamId()
    }

    private fun nextStreamId(): Int = streamId.next(state.receivers)

}
