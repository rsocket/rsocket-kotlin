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

package io.rsocket.kotlin.benchmarks

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

abstract class RSocketBenchmark<Payload : Any, Blackhole : Any> {

    // payload operations
    abstract val payloadSize: Int
    abstract fun createPayload(size: Int): Payload
    abstract fun createPayloadCopy(): Payload
    abstract fun releasePayload(payload: Payload)

    // something wrong with compilation here
    abstract fun consumePayload(bh: Blackhole, value: Payload)

    // lifecycle

    abstract fun setup()
    abstract fun cleanup()

    // benchmarks

    // Benchmark annotation doesn't inherit on jvm
    open fun requestResponseBlocking(bh: Blackhole) = blocking(bh, ::requestResponse)
    open fun requestResponseParallel(bh: Blackhole) = parallel(bh, 1000, ::requestResponse)
    open fun requestResponseConcurrent(bh: Blackhole) = concurrent(bh, 1000, ::requestResponse)


    // operations

    abstract suspend fun doRequestResponse(): Payload
    abstract fun doRequestStream(): Flow<Payload>
    abstract fun doRequestChannel(): Flow<Payload>

    private suspend fun requestResponse(bh: Blackhole) {
        doRequestResponse().also {
            releasePayload(it)
            consumePayload(bh, it)
        }
    }

    private suspend fun requestStream(bh: Blackhole) {
        doRequestStream().collect {
            releasePayload(it)
            consumePayload(bh, it)
        }
    }

    private suspend fun requestChannel(bh: Blackhole) {
        doRequestChannel().collect {
            releasePayload(it)
            consumePayload(bh, it)
        }
    }

    // execution strategies

    // plain blocking
    private inline fun blocking(
        bh: Blackhole,
        crossinline block: suspend (bh: Blackhole) -> Unit,
    ): Unit = runBlocking {
        block(bh)
    }

    // Run every request in a separate coroutine which will be dispatched on Default dispatcher (thread amount = cores amount)
    private inline fun parallel(
        bh: Blackhole,
        p: Int,
        crossinline block: suspend (bh: Blackhole) -> Unit,
    ): Unit = runBlocking(Dispatchers.Default) {
        repeat(p) { launch { block(bh) } }
    }

    // Run every request in separate coroutine, but on single thread dispatcher
    private inline fun concurrent(
        bh: Blackhole,
        p: Int,
        crossinline block: suspend (bh: Blackhole) -> Unit,
    ): Unit = runBlocking {
        repeat(p) { launch { block(bh) } }
    }
}
