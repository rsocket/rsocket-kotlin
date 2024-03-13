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

package io.rsocket.kotlin.operation

import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@ExperimentalStreamsApi
internal inline fun requestFlow(
    crossinline block: suspend FlowCollector<Payload>.(strategy: RequestStrategy.Element, initialRequest: Int) -> Unit,
): Flow<Payload> = object : RequestFlow() {
    override suspend fun FlowCollector<Payload>.collect(strategy: RequestStrategy.Element, initialRequest: Int) {
        return block(strategy, initialRequest)
    }
}

@ExperimentalStreamsApi
internal suspend fun FlowCollector<Payload>.emitAllWithRequestN(
    payloads: ReceiveChannel<Payload>,
    requestNs: SendChannel<Int>,
    strategy: RequestStrategy.Element,
): Throwable? {
    while (true) {
        val result = payloads.receiveCatching()
        if (result.isClosed) return result.exceptionOrNull()
        val payload = result.getOrThrow() // will never throw
        try {
            emit(payload)
        } catch (cause: Throwable) {
            payload.close()
            throw cause
        }

        @OptIn(DelicateCoroutinesApi::class)
        if (requestNs.isClosedForSend) continue

        val next = strategy.nextRequest()
        if (next <= 0) continue

        // if this fails, it's means that requests no longer possible;
        // next payloads.receiveCatching() should return a closed state
        requestNs.trySend(next)
    }
}

@ExperimentalStreamsApi
internal abstract class RequestFlow : Flow<Payload> {
    private val consumed = atomic(false)

    override suspend fun collect(collector: FlowCollector<Payload>) {
        check(!consumed.getAndSet(true)) { "RequestFlow can be collected just once" }

        val strategy = currentCoroutineContext().requestStrategy()
        val initialRequest = strategy.firstRequest()
        collector.collect(strategy, initialRequest)
    }

    abstract suspend fun FlowCollector<Payload>.collect(strategy: RequestStrategy.Element, initialRequest: Int)
}
