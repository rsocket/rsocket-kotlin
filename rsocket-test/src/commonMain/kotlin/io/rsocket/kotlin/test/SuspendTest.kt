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

    val debug: Boolean get() = true //change to turn off debug logs locally (useful for CI)

    suspend fun before(): Unit = Unit
    suspend fun after(): Unit = Unit

    fun test(
        timeout: Duration = testTimeout,
        ignoreNative: Boolean = false,
        block: suspend CoroutineScope.() -> Unit,
    ) = runTest(ignoreNative = ignoreNative) {

        val beforeError = runPhase("BEFORE", beforeTimeout) { before() }

        val testError = when (beforeError) { //don't run test if before failed
            null -> runPhase("RUN", timeout, block)
            else -> null
        }

        val afterError = runPhase("AFTER", afterTimeout) { after() }

        handleErrors(testError, listOf(beforeError, afterError))
    }

    //suppresses errors if more than one
    private fun handleErrors(error: Throwable?, other: List<Throwable?>) {
        when (error) {
            null -> {
                if (other.isEmpty()) return
                handleErrors(other.first(), other.drop(1))
            }
            else -> {
                other.forEach { it?.let(error::addSuppressed) }
                throw error
            }
        }
    }

    private suspend fun runPhase(tag: String, timeout: Duration, block: suspend CoroutineScope.() -> Unit): Throwable? {
        if (debug) println("[TEST] $tag started")
        val error = runCatching {
            withTimeout(timeout, block)
        }.exceptionOrNull()
        if (debug) when (error) {
            null                            -> println("[TEST] $tag completed")
            is TimeoutCancellationException -> println("[TEST] $tag failed by timeout: $timeout")
            else                            -> println("[TEST] $tag failed with error: $error")
        }
        return error
    }

    suspend fun currentJob(): Job = coroutineContext[Job]!!
}
