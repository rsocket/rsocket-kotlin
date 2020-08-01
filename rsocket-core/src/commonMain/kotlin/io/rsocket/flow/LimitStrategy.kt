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

package io.rsocket.flow

import kotlinx.atomicfu.*
import kotlinx.coroutines.*

class LimitStrategy(override val initialRequest: Int) : RequestStrategy {
    private val requests = atomic(initialRequest)
    private val awaiter = atomic<CancellableContinuation<Unit>?>(null)

    override suspend fun requestOnEmit(): Int {
//        println("USE")
        useRequest()
        return 0
    }

    private suspend fun useRequest() {
        if (requests.value <= 0) {
//            println("WAIT")
            suspendCancellableCoroutine<Unit> {
                awaiter.value = it
                if (requests.value != 0) it.resumeSafely()
            }
        }
        requests.decrementAndGet()
    }

    fun saveRequest(n: Int) {
//        println("SAVE")
        requests.getAndAdd(n)
        awaiter.getAndSet(null)?.resumeSafely()
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun CancellableContinuation<Unit>.resumeSafely() {
        val token = tryResume(Unit)
        if (token != null) completeResume(token)
    }
}
