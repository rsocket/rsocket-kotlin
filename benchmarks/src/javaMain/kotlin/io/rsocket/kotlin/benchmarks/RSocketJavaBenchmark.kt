/*
 * Copyright 2015-2020 the original author or authors.
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

import io.rsocket.*
import io.rsocket.core.*
import io.rsocket.frame.decoder.*
import io.rsocket.transport.local.*
import io.rsocket.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.*
import org.reactivestreams.*
import reactor.core.publisher.*
import kotlin.random.*

class RSocketJavaBenchmark : RSocketBenchmark<Payload>() {

    lateinit var client: RSocket
    lateinit var server: Closeable

    lateinit var payload: Payload
    lateinit var payloadMono: Mono<Payload>
    lateinit var payloadsFlux: Flux<Payload>
    lateinit var payloadsFlow: Flow<Payload>

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
            .bind(LocalServerTransport.create("server"))
            .block()!!

        client = RSocketConnector.create()
            .payloadDecoder(PayloadDecoder.ZERO_COPY)
            .connect(LocalClientTransport.create("server"))
            .block()!!
    }

    override fun cleanup() {
        client.dispose()
        server.dispose()
    }

    override fun createPayload(size: Int): Payload = if (size == 0) EmptyPayload.INSTANCE else ByteBufPayload.create(
        ByteArray(size / 2).also { Random.nextBytes(it) },
        ByteArray(size / 2).also { Random.nextBytes(it) }
    )

    override fun releasePayload(payload: Payload) {
        payload.release()
    }

    override suspend fun doRequestResponse(): Payload = client.requestResponse(payload.retain()).awaitSingle()

    override suspend fun doRequestStream(): Flow<Payload> = client.requestStream(payload.retain()).asFlow()

    override suspend fun doRequestChannel(): Flow<Payload> = client.requestChannel(payloadsFlow.asPublisher()).asFlow()

}
