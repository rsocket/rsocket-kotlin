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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@ExperimentalStreamsApi
internal inline fun requestFlow(
    crossinline block: suspend FlowCollector<Payload>.(strategy: RequestStrategy.Element, initialRequest: Int) -> Unit
): Flow<Payload> = object : RequestFlow() {
    override suspend fun collect(collector: FlowCollector<Payload>, strategy: RequestStrategy.Element, initialRequest: Int) {
        collector.block(strategy, initialRequest)
    }
}

@ExperimentalStreamsApi
internal suspend inline fun FlowCollector<Payload>.emitAllWithRequestN(
    channel: ReceiveChannel<Payload>,
    strategy: RequestStrategy.Element,
    crossinline onRequest: suspend (n: Int) -> Unit,
) {
    val collector = object : RequestFlowCollector(this, strategy) {
        override suspend fun onRequest(n: Int) {
            @OptIn(ExperimentalCoroutinesApi::class)
            if (!channel.isClosedForReceive) onRequest(n)
        }
    }
    collector.emitAll(channel)
}

@ExperimentalStreamsApi
internal abstract class RequestFlow : Flow<Payload> {
    private val consumed = atomic(false)

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<Payload>) {
        check(!consumed.getAndSet(true)) { "RequestFlow can be collected just once" }

        val strategy = currentCoroutineContext().requestStrategy()
        val initial = strategy.firstRequest()
        collect(collector, strategy, initial)
    }

    abstract suspend fun collect(collector: FlowCollector<Payload>, strategy: RequestStrategy.Element, initialRequest: Int)
}

@ExperimentalStreamsApi
internal abstract class RequestFlowCollector(
    private val collector: FlowCollector<Payload>,
    private val strategy: RequestStrategy.Element,
) : FlowCollector<Payload> {
    override suspend fun emit(value: Payload): Unit = value.closeOnError {
        collector.emit(value)
        val next = strategy.nextRequest()
        if (next > 0) onRequest(next)
    }

    abstract suspend fun onRequest(n: Int)
}
