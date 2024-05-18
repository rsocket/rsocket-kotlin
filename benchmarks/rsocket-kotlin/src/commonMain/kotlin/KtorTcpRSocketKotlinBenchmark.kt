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

import io.ktor.network.selector.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class KtorTcpRSocketKotlinBenchmark : RSocketKotlinBenchmark() {
    @Param("0")
    override var payloadSize: Int = 0

    @Param("")
    var dispatcher: String = ""

    @Param("")
    var selectorDispatcher: String = ""

    private val dispatcherV by lazy {
        when (dispatcher) {
            "DEFAULT"    -> Dispatchers.Default
            "IO"         -> Dispatchers.IO
            "UNCONFINED" -> Dispatchers.Unconfined
            else         -> error("wrong parameter 'dispatcher=$dispatcher'")
        }
    }

    private val selectorDispatcherV by lazy {
        when (selectorDispatcher) {
            "DEFAULT" -> Dispatchers.Default
            "IO"      -> Dispatchers.IO
            "1"       -> newSingleThreadContext("selectorDispatcher")
            "2"       -> newFixedThreadPoolContext(2, "selectorDispatcher")
            "4"       -> newFixedThreadPoolContext(4, "selectorDispatcher")
            "8"       -> newFixedThreadPoolContext(8, "selectorDispatcher")
            "l1"      -> Dispatchers.IO.limitedParallelism(1)
            "l2"      -> Dispatchers.IO.limitedParallelism(2)
            "l4"      -> Dispatchers.IO.limitedParallelism(4)
            "l8"      -> Dispatchers.IO.limitedParallelism(8)
            else      -> error("wrong parameter 'selectorDispatcher=$selectorDispatcher'")
        }
    }

    private val selector by lazy {
        SelectorManager(selectorDispatcherV)
    }

    override val serverTarget: RSocketServerTarget<*> by lazy {
        KtorTcpServerTransport(benchJob) {
            dispatcher(dispatcherV)
            selectorManager(selector, manage = false)
        }.target(port = 9000)
    }

    override val clientTarget: RSocketClientTarget by lazy {
        KtorTcpClientTransport(benchJob) {
            dispatcher(dispatcherV)
            selectorManager(selector, manage = false)
        }.target("0.0.0.0", port = 9000)
    }

    @Setup
    override fun setup() {
        super.setup()
    }

    @TearDown
    override fun cleanup() {
        super.cleanup()
        selector.close()
        if (
            selectorDispatcherV != Dispatchers.Default &&
            selectorDispatcherV != Dispatchers.IO &&
            selectorDispatcherV is CloseableCoroutineDispatcher
        ) {
            (selectorDispatcherV as CloseableCoroutineDispatcher).close()
        }
    }
}
