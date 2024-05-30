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

import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

internal suspend inline fun Flow<Payload>.collectLimiting(
    limiter: PayloadLimiter,
    crossinline action: suspend (value: Payload) -> Unit,
) {
    collect { payload ->
        try {
            limiter.useRequest()
        } catch (cause: Throwable) {
            payload.close()
            throw cause
        }
        action(payload)
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
 */
internal class PayloadLimiter(initial: Int) : SynchronizedObject() {
    private val requests = atomic(initial)
    private val requestNs = Channel<Int>(Channel.UNLIMITED)

    fun updateRequests(n: Int) {
        if (n <= 0) return
        requestNs.trySend(n)
    }

    suspend fun useRequest() {
        if (requests.decrementAndGet() > 0) {
            currentCoroutineContext().ensureActive()
        } else {
            requests.value = requestNs.receive()
        }
    }
}
