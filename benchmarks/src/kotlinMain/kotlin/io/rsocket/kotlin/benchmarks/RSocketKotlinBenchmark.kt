/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.benchmarks

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.*

@OptIn(ExperimentalStreamsApi::class, DelicateCoroutinesApi::class)
class RSocketKotlinBenchmark : RSocketBenchmark<Payload>() {
    private val requestStrategy = PrefetchStrategy(64, 0)

    private val benchJob = Job()
    lateinit var client: RSocket

    lateinit var payload: Payload
    lateinit var payloadsFlow: Flow<Payload>

    fun payloadCopy(): Payload = payload.copy()

    override fun setup() {
        payload = createPayload(payloadSize)
        payloadsFlow = flow { repeat(5000) { emit(payloadCopy()) } }
        val server = RSocketServer().bindIn(CoroutineScope(benchJob + Dispatchers.Unconfined), LocalServerTransport()) {
            RSocketRequestHandler {
                requestResponse {
                    it.close()
                    payloadCopy()
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
        client = runBlocking {
            RSocketConnector().connect(server)
        }
    }

    override fun cleanup() {
        runBlocking {
            client.coroutineContext.job.cancelAndJoin()
            benchJob.cancelAndJoin()
        }
    }

    override fun createPayload(size: Int): Payload = if (size == 0) Payload.Empty else Payload(
        data = ByteReadPacket(ByteArray(size / 2).also { Random.nextBytes(it) }),
        metadata = ByteReadPacket(ByteArray(size / 2).also { Random.nextBytes(it) })
    )

    override fun releasePayload(payload: Payload) {
        payload.close()
    }

    override suspend fun doRequestResponse(): Payload = client.requestResponse(payloadCopy())

    override suspend fun doRequestStream(): Flow<Payload> = client.requestStream(payloadCopy()).flowOn(requestStrategy)

    override suspend fun doRequestChannel(): Flow<Payload> =
        client.requestChannel(payloadCopy(), payloadsFlow).flowOn(requestStrategy)

}
