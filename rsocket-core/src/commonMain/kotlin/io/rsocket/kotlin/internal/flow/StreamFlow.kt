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
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.internal.*
import kotlin.coroutines.*

@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal abstract class StreamFlow(
    protected val state: RSocketState,
    context: CoroutineContext,
    capacity: Int,
) : ChannelFlow<Payload>(context, capacity) {

    protected val requestSize: Int
        get() = when (capacity) {
            Channel.CONFLATED -> Int.MAX_VALUE // request all and conflate incoming
            Channel.RENDEZVOUS -> 1 // need to request at least one anyway
            Channel.UNLIMITED -> Int.MAX_VALUE
            Channel.BUFFERED -> 64
            else               -> capacity.also { check(it >= 1) }
        }

    protected abstract suspend fun collectImpl(collectContext: CoroutineContext, collector: FlowCollector<Payload>)

    final override suspend fun collect(collector: FlowCollector<Payload>) {
        val collectContext = context + coroutineContext
        withContext(coroutineContext + context) {
            collectImpl(collectContext, collector)
        }
    }

    final override suspend fun collectTo(scope: ProducerScope<Payload>): Unit =
        collectImpl(scope.coroutineContext, SendingCollector(scope.channel))

    protected suspend fun collectStream(
        streamId: Int,
        receiver: ReceiveChannel<RequestFrame>,
        collectContext: CoroutineContext,
        collector: FlowCollector<Payload>,
    ): Unit = with(state) {
        consumeReceiverFor(streamId) {
            var consumed = 0
            //TODO fragmentation
            for (frame in receiver) {
                if (frame.complete) return //TODO check next flag
                //emit in collectContext to prevent `Flow invariant is violated`
                withContext(collectContext) {
                    collector.emit(frame.payload)
                }
                if (++consumed == requestSize) {
                    consumed = 0
                    send(RequestNFrame(streamId, requestSize))
                }
            }
        }
    }
}
