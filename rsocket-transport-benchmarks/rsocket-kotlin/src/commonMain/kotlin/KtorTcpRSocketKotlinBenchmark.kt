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

package io.rsocket.kotlin.transport.benchmarks.kotlin

import io.ktor.network.selector.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.benchmarks.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = WARMUP, time = WARMUP_DURATION)
@Measurement(iterations = ITERATION, time = ITERATION_DURATION)
@State(Scope.Benchmark)
class KtorTcpRSocketKotlinBenchmark : RSocketKotlinBenchmark() {

    private val selector by lazy {
        SelectorManager(Dispatchers.IO)
    }

    override val serverTarget: RSocketServerTarget<*> by lazy {
        KtorTcpServerTransport(benchJob + CoroutineExceptionHandler { _, _ -> }) {
            selectorManager(selector, manage = false)
        }.target()
    }

    override fun clientTarget(serverInstance: RSocketServerInstance): RSocketClientTarget {
        return KtorTcpClientTransport(benchJob + CoroutineExceptionHandler { _, _ -> }) {
            selectorManager(selector, manage = false)
        }.target((serverInstance as KtorTcpServerInstance).localAddress)
    }

    @Setup
    override fun setup() {
        super.setup()
    }

    @TearDown
    override fun cleanup() {
        super.cleanup()
        selector.close()
    }
}
