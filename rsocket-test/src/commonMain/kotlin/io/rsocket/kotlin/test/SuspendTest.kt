/*
 * Copyright 2015-2022 the original author or authors.
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val TestExceptionHandler = CoroutineExceptionHandler { c, e ->
    println("Error in $c -> ${e.stackTraceToString()}")
}

interface SuspendTest {
    val testTimeout: Duration get() = 1.minutes

    val beforeTimeout: Duration get() = 10.seconds
    val afterTimeout: Duration get() = 10.seconds

    val debug: Boolean get() = true //change to turn off debug logs locally (useful for CI)

    suspend fun before(): Unit = Unit
    suspend fun after(): Unit = Unit

    fun test(
        timeout: Duration = testTimeout,
        block: suspend CoroutineScope.() -> Unit,
    ) = runTest {

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
        println("[TEST] $tag started")
        return when (val result = runWithTimeout(timeout, block)) {
            is TestResult.Success -> {
                println("[TEST] $tag completed in ${result.duration}")
                null
            }
            is TestResult.Failed  -> {
                println("[TEST] $tag failed in ${result.duration} with error: ${result.cause.stackTraceToString()}")
                result.cause
            }
            is TestResult.Timeout -> {
                println("[TEST] $tag failed by timeout: ${result.timeout}")
                result.cause
            }
        }
    }

    private sealed interface TestResult {
        class Success(val duration: Duration) : TestResult
        class Failed(val duration: Duration, val cause: Throwable) : TestResult
        class Timeout(val timeout: Duration, val cause: Throwable) : TestResult
    }

    private suspend fun runWithTimeout(timeout: Duration, block: suspend CoroutineScope.() -> Unit): TestResult =
        runCatching {
            withTimeout(timeout) {
                measureTimedValue {
                    runCatching {
                        block()
                    }
                }
            }
        }.fold(
            onSuccess = { (result, duration) ->
                result.fold(
                    onSuccess = { TestResult.Success(duration) },
                    onFailure = { TestResult.Failed(duration, it) }
                )
            },
            onFailure = { TestResult.Timeout(timeout, it) }
        )

    suspend fun currentJob(): Job = coroutineContext[Job]!!
}
