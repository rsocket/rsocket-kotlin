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

import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

internal suspend inline fun Flow<Payload>.collectLimiting(limiter: Limiter, crossinline action: suspend (value: Payload) -> Unit) {
    collect { payload ->
        payload.closeOnError {
            limiter.useRequest()
            action(it)
        }
    }
}

/**
 * Maintains the amount of requests which the client is ready to consume and
 * prevents sending further updates by suspending the sending coroutine
 * if this amount reaches 0.
 *
 * ### Operation
 *
 * Each [useRequest] call decrements the maintained requests amount.
 * Calling coroutine is suspended when this amount reaches 0.
 * The coroutine is resumed when [updateRequests] is called.
 *
 * ### Unbounded mode
 *
 * Limiter enters an unbounded mode when:
 * * [Limiter] is created passing `Int.MAX_VALUE` as `initial`
 * * client sends a `RequestN` frame with `Int.MAX_VALUE`
 * * Internal Long counter overflows
 *
 * In unbounded mode Limiter will assume that the client
 * is able to process requests without limitations, all further
 * [updateRequests] will be NOP and [useRequest] will never suspend.
 */
internal class Limiter(initial: Int) : SynchronizedObject() {
    private val requests: AtomicLong = atomic(initial.toLong())
    private val unbounded: AtomicBoolean = atomic(initial == Int.MAX_VALUE)
    private var awaiter: CancellableContinuation<Unit>? = null

    fun updateRequests(n: Int) {
        if (n <= 0 || unbounded.value) return
        synchronized(this) {
            val updatedRequests = requests.value + n.toLong()
            if (updatedRequests < 0) {
                unbounded.value = true
                requests.value = Long.MAX_VALUE
            } else {
                requests.value = updatedRequests
            }

            if (awaiter?.isActive == true) {
                awaiter?.resume(Unit)
                awaiter = null
            }
        }
    }

    suspend fun useRequest() {
        if (unbounded.value || requests.decrementAndGet() >= 0) {
            currentCoroutineContext().ensureActive()
        } else {
            suspendCancellableCoroutine<Unit> { continuation ->
                synchronized(this) {
                    if (requests.value >= 0 && continuation.isActive) {
                        continuation.resume(Unit)
                    } else {
                        this.awaiter = continuation
                    }
                }
            }
        }
    }
}
