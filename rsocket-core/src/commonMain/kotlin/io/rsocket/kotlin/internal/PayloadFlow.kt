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

package io.rsocket.kotlin.internal

import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@ExperimentalStreamsApi
internal inline fun payloadFlow(
    crossinline block: suspend FlowCollector<Payload>.(strategy: RequestStrategy.Element, initialRequest: Int) -> Unit,
): Flow<Payload> = object : PayloadFlow() {
    override suspend fun collect(collector: FlowCollector<Payload>, strategy: RequestStrategy.Element, initialRequest: Int) {
        return collector.block(strategy, initialRequest)
    }
}

@ExperimentalStreamsApi
internal abstract class PayloadFlow : Flow<Payload> {
    private val consumed = atomic(false)

    override suspend fun collect(collector: FlowCollector<Payload>) {
        check(!consumed.getAndSet(true)) { "RequestFlow can be collected just once" }

        val strategy = currentCoroutineContext().requestStrategy()
        val initialRequest = strategy.firstRequest()
        collect(collector, strategy, initialRequest)
    }

    abstract suspend fun collect(collector: FlowCollector<Payload>, strategy: RequestStrategy.Element, initialRequest: Int)
}
