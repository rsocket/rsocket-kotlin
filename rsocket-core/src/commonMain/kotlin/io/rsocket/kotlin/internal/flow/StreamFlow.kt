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

    protected abstract suspend fun collectImpl(collector: FlowCollector<Payload>)

    final override suspend fun collect(collector: FlowCollector<Payload>) {
        val collectContext = coroutineContext
        val newContext = collectContext + context
        // fast path #1 -- same context, just collect
        if (newContext == collectContext) return collectImpl(collector)

        // fast path #2 -- when dispatcher doesn't changed, no channel needed
        // can be optimized using flowOn (internal API)
        val newDispatcher = context[ContinuationInterceptor]
        if (newDispatcher == null || newDispatcher == collectContext[ContinuationInterceptor]) {
            return object : Flow<Payload> {
                override suspend fun collect(collector: FlowCollector<Payload>) = collectImpl(collector)
            }.flowOn(context).collect(collector)
        }

        // slow path -- create channel
        // TODO in that case RequestN frame can be sent even if it not needed because of asynchronously channel consumption
        //  f.e. if to do `flow.buffer(3).flowOn(Dispatchers.IO).take(3).collect()
        //  here because of changing dispatcher, new channel will be created
        super.collect(collector)
    }

    final override suspend fun collectTo(scope: ProducerScope<Payload>): Unit = collectImpl(SendingCollector(scope.channel))

    protected suspend fun collectStream(
        streamId: Int,
        receiver: ReceiveChannel<RequestFrame>,
        collector: FlowCollector<Payload>,
    ): Unit = with(state) {
        consumeReceiverFor(streamId) {
            var consumed = 0
            //TODO fragmentation
            for (frame in receiver) {
                if (frame.complete) return //TODO check next flag
                collector.emit(frame.payload)
                if (++consumed == requestSize) {
                    consumed = 0
                    send(RequestNFrame(streamId, requestSize))
                }
            }
        }
    }
}
