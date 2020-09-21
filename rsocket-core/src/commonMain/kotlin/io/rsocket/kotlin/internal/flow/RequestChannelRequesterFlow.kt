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

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.cancelConsumed
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

internal class RequestChannelRequesterFlow(
    private val payloads: Flow<Payload>,
    private val requester: RSocketRequester,
    state: RSocketState,
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.BUFFERED,
) : StreamFlow(state, context, capacity) {
    override fun create(context: CoroutineContext, capacity: Int): RequestChannelRequesterFlow =
        RequestChannelRequesterFlow(payloads, requester, state, context, capacity)

    override suspend fun collectImpl(collector: FlowCollector<Payload>): Unit = with(state) {
        val streamId = requester.createStream()
        val receiverDeferred = CompletableDeferred<ReceiveChannel<RequestFrame>?>()
        val request = launchCancelable(streamId) {
            payloads.collectLimiting(
                streamId,
                RequestChannelRequesterFlowCollector(state, streamId, receiverDeferred, requestSize)
            )
        }
        request.invokeOnCompletion {
            if (receiverDeferred.isCompleted) {
                @OptIn(ExperimentalCoroutinesApi::class)
                if (it != null && it !is CancellationException) receiverDeferred.getCompleted()?.cancelConsumed(it)
            } else {
                if (it == null) receiverDeferred.complete(null)
                else receiverDeferred.completeExceptionally(it.cause ?: it)
            }
        }
        try {
            val receiver = receiverDeferred.await() ?: return
            collectStream(streamId, receiver, collector)
        } catch (e: Throwable) {
            if (e is CancellationException) request.cancel(e)
            else request.cancel("Receiver failed", e)
            throw e
        }
    }
}
