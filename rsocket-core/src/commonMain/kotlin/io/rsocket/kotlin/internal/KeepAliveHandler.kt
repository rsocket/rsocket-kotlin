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
import io.rsocket.kotlin.keepalive.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

internal class KeepAliveHandler(private val keepAlive: KeepAlive) {
    private val lastMark = atomic(currentMillis()) // mark initial timestamp for keepalive

    fun mark() {
        lastMark.value = currentMillis()
    }

    // return boolean because of native
    suspend fun tick() {
        delay(keepAlive.intervalMillis.toLong())
        if (currentMillis() - lastMark.value < keepAlive.maxLifetimeMillis) return
        throw RSocketError.ConnectionError("No keep-alive for ${keepAlive.maxLifetimeMillis} ms")
    }
}

internal expect fun currentMillis(): Long
