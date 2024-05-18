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

package io.rsocket.kotlin.benchmarks.kotlin

import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class LocalRSocketKotlinOldBenchmark : RSocketKotlinOldBenchmark() {
    @Param("0")
    override var payloadSize: Int = 0

    @Param("")
    var dispatcher: String = ""

    override val serverDispatcher: CoroutineDispatcher by lazy {
        when (dispatcher) {
            "DEFAULT"    -> Dispatchers.Default
            "IO"         -> Dispatchers.IO
            "UNCONFINED" -> Dispatchers.Unconfined
            "1"          -> newSingleThreadContext("dispatcher")
            else         -> error("wrong parameter 'dispatcher=$dispatcher'")
        }
    }
    override val serverTransport: ServerTransport<*> by lazy {
        LocalServerTransport()
    }

    override suspend fun clientTransport(server: Any?): ClientTransport = server as LocalServer

    @Setup
    override fun setup() {
        super.setup()
    }

    @TearDown
    override fun cleanup() {
        super.cleanup()
        (serverDispatcher as? CloseableCoroutineDispatcher)?.close()
    }
}
