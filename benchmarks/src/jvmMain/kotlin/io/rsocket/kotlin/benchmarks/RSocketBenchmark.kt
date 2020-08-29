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

@BenchmarkMode(Mode.Throughput)
@Fork(value = 2)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 7, time = 10)
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
    fun requestResponseBlocking(bh: Blackhole) = blocking(bh, ::requestResponse)

    @Benchmark
    fun requestResponseParallel(bh: Blackhole) = parallel(bh, 500, ::requestResponse)

    @Benchmark
    fun requestResponseConcurrent(bh: Blackhole) = concurrent(bh, 500, ::requestResponse)


    @Benchmark
    fun requestStreamBlocking(bh: Blackhole) = blocking(bh, ::requestStream)

    @Benchmark
    fun requestStreamParallel(bh: Blackhole) = parallel(bh, 10, ::requestStream)

    @Benchmark
    fun requestStreamConcurrent(bh: Blackhole) = concurrent(bh, 10, ::requestStream)


    @Benchmark
    fun requestChannelBlocking(bh: Blackhole) = blocking(bh, ::requestChannel)

    @Benchmark
    fun requestChannelParallel(bh: Blackhole) = parallel(bh, 3, ::requestChannel)

    @Benchmark
    fun requestChannelConcurrent(bh: Blackhole) = concurrent(bh, 3, ::requestChannel)


    private suspend fun requestResponse(bh: Blackhole) {
        doRequestResponse().also {
            releasePayload(it)
            bh.consume(it)
        }
    }

    private suspend fun requestStream(bh: Blackhole) {
        doRequestStream().collect {
            releasePayload(it)
            bh.consume(it)
        }
    }

    private suspend fun requestChannel(bh: Blackhole) {
        doRequestChannel().collect {
            releasePayload(it)
            bh.consume(it)
        }
    }

    //plain blocking
    private inline fun blocking(bh: Blackhole, crossinline block: suspend (bh: Blackhole) -> Unit): Unit = runBlocking {
        block(bh)
    }

    //Run every request in separate coroutine which will be dispatched on Default dispatcher (threads amount = cores amount)
    private inline fun parallel(bh: Blackhole, p: Int, crossinline block: suspend (bh: Blackhole) -> Unit): Unit = runBlocking {
        (0..p).map {
            GlobalScope.async { block(bh) }
        }.awaitAll()
    }

    //Run every request in separate coroutine, but on single thread dispatcher:
    //  - do request 1
    //  - suspend on awaiting of result 1
    //  - do request 2
    //  - suspend on awaiting of result 2
    //  - receive result on request 1
    //  - receive result on request 2
    //  - ....
    //working with requests is single threaded but concurrent
    private inline fun concurrent(bh: Blackhole, p: Int, crossinline block: suspend (bh: Blackhole) -> Unit): Unit = runBlocking {
        (0..p).map {
            async { block(bh) }
        }.awaitAll()
    }
}
