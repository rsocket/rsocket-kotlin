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
import io.netty.channel.nio.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import io.rsocket.kotlin.transport.netty.tcp.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
class NettyTcpRSocketKotlinBenchmark : RSocketKotlinBenchmark() {
    @Param("0")
    override var payloadSize: Int = 0

    override val serverTarget: RSocketServerTarget<*> by lazy {
        NettyTcpServerTransport(benchJob).target()
    }

    override fun clientTarget(serverInstance: RSocketServerInstance): RSocketClientTarget {
        return NettyTcpClientTransport(benchJob).target(
            (serverInstance as NettyTcpServerInstance).localAddress
        )
    }

    @Setup
    override fun setup() {
        super.setup()
    }

    @TearDown
    override fun cleanup() {
        super.cleanup()
    }
}
