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

package io.rsocket.kotlin.test

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.*

interface SuspendTest {
    val testTimeout: Duration get() = 1.minutes

    val beforeTimeout: Duration get() = 10.seconds
    val afterTimeout: Duration get() = 10.seconds

    val debug: Boolean get() = false //change to debug tests for additional logs

    suspend fun before(): Unit = Unit
    suspend fun after(): Unit = Unit

    fun test(
        timeout: Duration = testTimeout,
        ignoreNative: Boolean = false,
        block: suspend CoroutineScope.() -> Unit,
    ) = runTest(ignoreNative = ignoreNative) {

        runCatching {
            if (debug) println("[TEST] BEFORE started")
            withTimeout(beforeTimeout) { before() }
        }.onSuccess {
            if (debug) println("[TEST] BEFORE completed")
        }.onFailure {
            if (debug) println("[TEST] BEFORE failed with error: ${it.stackTraceToString()}")
        }

        val result = runCatching {
            withTimeout(timeout) { block() }
        }

        runCatching {
            if (debug) println("[TEST] AFTER started")
            withTimeout(afterTimeout) { after() }
        }.onSuccess {
            if (debug) println("[TEST] AFTER completed")
        }.onFailure {
            if (debug) println("[TEST] AFTER failed with error: ${it.stackTraceToString()}")
        }

        result.getOrThrow()
    }

    suspend fun currentJob(): Job = coroutineContext[Job]!!
}
