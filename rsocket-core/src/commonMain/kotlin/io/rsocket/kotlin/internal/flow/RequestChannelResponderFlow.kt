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
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalStreamsApi::class)
internal class RequestChannelResponderFlow(
    private val streamId: Int,
    private val receiver: ReceiveChannel<RequestFrame>,
    private val state: RSocketState,
) : Flow<Payload> {
    private val consumed = atomic(false)

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<Payload>): Unit = with(state) {
        check(!consumed.getAndSet(true)) { "RSocket.requestChannel `payloads` can be collected just once" }

        val strategy = currentCoroutineContext().requestStrategy()
        val initialRequest = strategy.firstRequest()
        send(RequestNFrame(streamId, initialRequest))
        collectStream(streamId, receiver, strategy, collector)
    }
}
