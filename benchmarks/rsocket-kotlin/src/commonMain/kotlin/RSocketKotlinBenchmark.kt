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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.benchmarks.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.benchmark.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.*

@OptIn(ExperimentalStreamsApi::class)
abstract class RSocketKotlinBenchmark : RSocketBenchmark<Payload, Blackhole>() {
    protected abstract val clientTarget: RSocketClientTarget
    protected abstract val serverTarget: RSocketServerTarget<*>

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

        RSocketServer().startServer(serverTarget) {
            RSocketRequestHandler {
                requestResponse {
                    it.close()
                    createPayloadCopy()
                }
                requestStream {
                    it.close()
                    payloadsFlow
                }
                requestChannel { init, payloads ->
                    init.close()
                    payloads.flowOn(requestStrategy)
                }
            }
        }
        client = RSocketConnector().connect(clientTarget)
    }

    override fun cleanup(): Unit = runBlocking {
        client.coroutineContext.job.cancelAndJoin()
        benchJob.cancelAndJoin()
    }

    @Benchmark
    override fun requestResponseBlocking(bh: Blackhole) = super.requestResponseBlocking(bh)

    @Benchmark
    override fun requestResponseParallel(bh: Blackhole) = super.requestResponseParallel(bh)

    @Benchmark
    override fun requestResponseConcurrent(bh: Blackhole) = super.requestResponseConcurrent(bh)
}
