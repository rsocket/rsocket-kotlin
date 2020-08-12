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

package io.rsocket.kotlin.benchmarks

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.*
import java.util.concurrent.locks.*

private const val INTEGER = 10

@BenchmarkMode(Mode.Throughput)
@Fork(value = 2)
@Warmup(iterations = INTEGER)
@Measurement(iterations = INTEGER, time = INTEGER)
@State(Scope.Benchmark)
abstract class RSocketBenchmark<Payload : Any> {

    @Param("0", "64", "1024", "131072", "1048576", "15728640")
    var payloadSize: Int = 0

    @Setup
    abstract fun setup()

    @TearDown
    abstract fun cleanup()

    @TearDown(Level.Iteration)
    fun awaitToBeConsumed() {
        LockSupport.parkNanos(5000)
    }

    abstract fun createPayload(size: Int): Payload

    abstract fun releasePayload(payload: Payload)

    abstract suspend fun doRequestResponse(): Payload

    abstract suspend fun doRequestStream(): Flow<Payload>

    abstract suspend fun doRequestChannel(): Flow<Payload>

    @Benchmark
    fun requestResponse(bh: Blackhole) = runBlocking {
        doRequestResponse().also {
            releasePayload(it)
            bh.consume(it)
        }
    }

    @Benchmark
    fun requestStream(bh: Blackhole) = runBlocking {
        doRequestStream().collect {
            releasePayload(it)
            bh.consume(it)
        }
    }

    @Benchmark
    fun requestChannel(bh: Blackhole) = runBlocking {
        doRequestChannel().collect {
            releasePayload(it)
            bh.consume(it)
        }
    }

}
