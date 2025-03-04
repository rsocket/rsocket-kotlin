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

import io.ktor.network.sockets.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.benchmarks.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*
import kotlin.random.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = WARMUP, time = WARMUP_DURATION)
@Measurement(iterations = ITERATION, time = ITERATION_DURATION)
@State(Scope.Benchmark)
class KtorTcpRSocketKotlin_0_16_Benchmark : RSocketKotlin_0_16_Benchmark() {
    @Param("0")
    override var payloadSize: Int = 0

    override val serverDispatcher: CoroutineDispatcher = Dispatchers.IO

    override val serverTransport: ServerTransport<*> by lazy {
        TcpServerTransport(port = 9000 + Random.nextInt(100))
    }

    override suspend fun clientTransport(server: Any?): ClientTransport {
        return TcpClientTransport(
            (server as TcpServer).serverSocket.await().localAddress as InetSocketAddress
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
