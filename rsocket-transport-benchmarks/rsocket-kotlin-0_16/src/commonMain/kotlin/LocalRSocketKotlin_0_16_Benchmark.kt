/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.transport.benchmarks.kotlin_0_16

import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.benchmarks.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = WARMUP, time = WARMUP_DURATION)
@Measurement(iterations = ITERATION, time = ITERATION_DURATION)
@State(Scope.Benchmark)
class LocalRSocketKotlin_0_16_Benchmark : RSocketKotlin_0_16_Benchmark() {
    @Param("")
    var dispatcher: String = ""

    override val serverDispatcher: CoroutineDispatcher by lazy {
        when (dispatcher) {
            "DEFAULT"    -> Dispatchers.Default
            "UNCONFINED" -> Dispatchers.Unconfined
            else         -> error("wrong parameter 'dispatcher=$dispatcher'")
        }
    }
    override val serverTransport: ServerTransport<*> by lazy {
        LocalServerTransport()
    }

    override suspend fun clientTransport(server: Any?): ClientTransport = server as LocalServer

    @Setup
    override fun setup() = super.setup()

    @TearDown
    override fun cleanup() = super.cleanup()
}
