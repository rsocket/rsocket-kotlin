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

package io.rsocket.kotlin.internal.flow

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalStreamsApi::class)
internal class RequestChannelRequesterFlow(
    private val initPayload: Payload,
    private val payloads: Flow<Payload>,
    private val requester: RSocketRequester,
    private val state: RSocketState,
) : Flow<Payload> {
    private val consumed = atomic(false)

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<Payload>): Unit = with(state) {
        check(!consumed.getAndSet(true)) { "RSocket.requestChannel can be collected just once" }

        val strategy = currentCoroutineContext().requestStrategy()
        val initialRequest = strategy.firstRequest()
        initPayload.closeOnError {
            val streamId = requester.createStream()
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
                collectStream(streamId, receiver, strategy, collector)
            } catch (e: Throwable) {
                if (e is CancellationException) request.cancel(e)
                else request.cancel("Receiver failed", e)
                throw e
            }
        }
    }
}
