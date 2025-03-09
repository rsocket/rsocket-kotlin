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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.benchmarks.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*
import kotlin.random.*

@State(Scope.Benchmark)
@Suppress("ClassName")
@OptIn(ExperimentalStreamsApi::class)
abstract class RSocketKotlin_0_16_Benchmark : RSocketTransportBenchmark<Payload, Blackhole>() {
    protected abstract suspend fun clientTransport(server: Any?): ClientTransport
    protected abstract val serverTransport: ServerTransport<*>
    protected abstract val serverDispatcher: CoroutineDispatcher

    private val requestStrategy = PrefetchStrategy(64, 0)

    protected val benchJob = Job()
    private lateinit var client: RSocket
    private lateinit var payload: Payload
    private lateinit var payloadsFlow: Flow<Payload>

    override fun createPayload(size: Int): Payload = if (size == 0) Payload.Empty else Payload(
        data = ByteReadPacket(ByteArray(size / 2).also { Random.nextBytes(it) }),
        metadata = ByteReadPacket(ByteArray(size / 2).also { Random.nextBytes(it) })
    )

    override fun createPayloadCopy(): Payload = payload.copy()
    override fun consumePayload(bh: Blackhole, value: Payload) = bh.consume(value)
    override fun releasePayload(payload: Payload) = payload.close()

    override suspend fun doRequestResponse(): Payload = client.requestResponse(createPayloadCopy())
    override fun doRequestStream(): Flow<Payload> = client.requestStream(createPayloadCopy()).flowOn(requestStrategy)
    override fun doRequestChannel(): Flow<Payload> = client.requestChannel(createPayloadCopy(), payloadsFlow).flowOn(requestStrategy)

    override fun setup(): Unit = runBlocking {
        payload = createPayload(payloadSize)
        payloadsFlow = flow { repeat(5000) { emit(createPayloadCopy()) } }

        val server = RSocketServer().bindIn(CoroutineScope(benchJob + serverDispatcher), serverTransport) {
            object : RSocket {
                override val coroutineContext: CoroutineContext = Job()
                override suspend fun requestResponse(payload: Payload): Payload {
                    payload.close()
                    return createPayloadCopy()
                }

                override fun requestStream(payload: Payload): Flow<Payload> {
                    payload.close()
                    return payloadsFlow
                }

                override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
                    initPayload.close()
                    return payloads.flowOn(requestStrategy)
                }
            }
        }
        client = RSocketConnector().connect(clientTransport(server))
    }

    override fun cleanup(): Unit = runBlocking {
        client.coroutineContext.job.cancelAndJoin()
        benchJob.cancelAndJoin()
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
