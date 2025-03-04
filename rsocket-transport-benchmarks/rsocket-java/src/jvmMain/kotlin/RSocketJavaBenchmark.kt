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

package io.rsocket.kotlin.transport.benchmarks.java

import io.rsocket.*
import io.rsocket.core.*
import io.rsocket.frame.decoder.*
import io.rsocket.kotlin.transport.benchmarks.*
import io.rsocket.transport.*
import io.rsocket.util.*
import kotlinx.benchmark.*
import kotlinx.benchmark.Mode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.*
import org.reactivestreams.*
import reactor.core.publisher.*
import kotlin.random.*

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = WARMUP, time = WARMUP_DURATION)
@Measurement(iterations = ITERATION, time = ITERATION_DURATION)
@State(Scope.Benchmark)
abstract class RSocketJavaBenchmark : RSocketTransportBenchmark<Payload, Blackhole>() {
    protected abstract val clientTransport: ClientTransport
    protected abstract val serverTransport: ServerTransport<*>

    private lateinit var payload: Payload
    private lateinit var payloadMono: Mono<Payload>
    private lateinit var payloadsFlux: Flux<Payload>
    private lateinit var payloadsFlow: Flow<Payload>
    private lateinit var client: RSocket
    private lateinit var server: Closeable

    override fun createPayload(size: Int): Payload = if (size == 0) EmptyPayload.INSTANCE else ByteBufPayload.create(
        ByteArray(size / 2).also { Random.nextBytes(it) },
        ByteArray(size / 2).also { Random.nextBytes(it) }
    )

    override fun createPayloadCopy(): Payload = payload.retain()

    override fun releasePayload(payload: Payload) {
        payload.release()
    }

    override fun consumePayload(bh: Blackhole, value: Payload) = bh.consume(value)

    override suspend fun doRequestResponse(): Payload = client.requestResponse(payload.retain()).awaitSingle()
    override fun doRequestStream(): Flow<Payload> = client.requestStream(payload.retain()).asFlow()
    override fun doRequestChannel(): Flow<Payload> = client.requestChannel(payloadsFlow.asPublisher()).asFlow()

    @Setup
    override fun setup() {
        payload = createPayload(payloadSize)
        payloadMono = Mono.fromSupplier(payload::retain)
        payloadsFlux = Flux.range(0, 5000).map { payload.retain() }
        payloadsFlow = flow { repeat(5000) { emit(payload.retain()) } }

        server = RSocketServer.create { _, _ ->
            Mono.just(
                object : RSocket {
                    override fun requestResponse(payload: Payload): Mono<Payload> {
                        payload.release()
                        return payloadMono
                    }

                    override fun requestStream(payload: Payload): Flux<Payload> {
                        payload.release()
                        return payloadsFlux
                    }

                    override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> = Flux.from(payloads)
                })
        }
            .payloadDecoder(PayloadDecoder.ZERO_COPY)
            .bind(serverTransport)
            .block()!!

        client = RSocketConnector.create()
            .payloadDecoder(PayloadDecoder.ZERO_COPY)
            .connect(clientTransport)
            .block()!!
    }

    @TearDown
    override fun cleanup() {
        client.dispose()
        server.dispose()
    }

    @Param("0")
    override var payloadSize: Int = 0

    @Benchmark
    override fun requestResponseBlocking(bh: Blackhole) = super.requestResponseBlocking(bh)

    @Benchmark
    override fun requestResponseParallel(bh: Blackhole) = super.requestResponseParallel(bh)

    @Benchmark
    override fun requestResponseConcurrent(bh: Blackhole) = super.requestResponseConcurrent(bh)

    @Benchmark
    override fun requestStreamBlocking(bh: Blackhole) = super.requestStreamBlocking(bh)

    @Benchmark
    override fun requestStreamParallel(bh: Blackhole) = super.requestStreamParallel(bh)

    @Benchmark
    override fun requestStreamConcurrent(bh: Blackhole) = super.requestStreamConcurrent(bh)

    @Benchmark
    override fun requestChannelBlocking(bh: Blackhole) = super.requestChannelBlocking(bh)

    @Benchmark
    override fun requestChannelParallel(bh: Blackhole) = super.requestChannelParallel(bh)

    @Benchmark
    override fun requestChannelConcurrent(bh: Blackhole) = super.requestChannelConcurrent(bh)
}
