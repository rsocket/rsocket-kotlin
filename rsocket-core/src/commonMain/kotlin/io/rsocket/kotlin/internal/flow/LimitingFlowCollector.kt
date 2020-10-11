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

import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal abstract class LimitingFlowCollector(initial: Int) : FlowCollector<Payload> {
    private val requests = atomic(initial)
    private val awaiter = atomic<CancellableContinuation<Unit>?>(null)

    abstract suspend fun emitValue(value: Payload)

    fun updateRequests(n: Int) {
        if (n <= 0) return
        requests.getAndAdd(n)
        awaiter.getAndSet(null)?.resumeSafely()
    }

    final override suspend fun emit(value: Payload): Unit = value.closeOnError {
        useRequest()
        emitValue(value)
    }

    private suspend fun useRequest() {
        if (requests.value <= 0) {
            suspendCancellableCoroutine<Unit> {
                awaiter.value = it
                if (requests.value != 0) it.resumeSafely()
            }
        }
        requests.decrementAndGet()
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun CancellableContinuation<Unit>.resumeSafely() {
        val token = tryResume(Unit)
        if (token != null) completeResume(token)
    }

}
