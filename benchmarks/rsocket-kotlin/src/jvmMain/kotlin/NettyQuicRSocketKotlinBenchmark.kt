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
import io.netty.handler.ssl.util.*
import io.netty.incubator.codec.quic.*
import io.rsocket.kotlin.benchmarks.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import io.rsocket.kotlin.transport.netty.quic.*
import io.rsocket.kotlin.transport.netty.tcp.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = WARMUP, time = WARMUP_DURATION)
@Measurement(iterations = ITERATION, time = ITERATION_DURATION)
@State(Scope.Benchmark)
class NettyQuicRSocketKotlinBenchmark : RSocketKotlinBenchmark() {
    private val certificates = SelfSignedCertificate()

    private val protos = arrayOf("hq-29")

    private val sharedGroup by lazy {
        NioEventLoopGroup()
    }

    override val serverTarget: RSocketServerTarget<*> by lazy {
        NettyQuicServerTransport(benchJob) {
            eventLoopGroup(sharedGroup, manage = false)
            ssl {
                keyManager(certificates.privateKey(), null, certificates.certificate())
                applicationProtocols(*protos)
            }
            codec {
                tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            }
        }.target("127.0.0.1")
    }

    override fun clientTarget(serverInstance: RSocketServerInstance): RSocketClientTarget {
        return NettyQuicClientTransport(benchJob) {
            eventLoopGroup(sharedGroup, manage = false)
            ssl {
                trustManager(InsecureTrustManagerFactory.INSTANCE)
                applicationProtocols(*protos)
            }
        }.target(
            (serverInstance as NettyQuicServerInstance).localAddress
        )
    }

    @Setup
    override fun setup() {
        super.setup()
    }

    @TearDown
    override fun cleanup() {
        super.cleanup()
        sharedGroup.shutdownGracefully().await(1000)
    }
}
