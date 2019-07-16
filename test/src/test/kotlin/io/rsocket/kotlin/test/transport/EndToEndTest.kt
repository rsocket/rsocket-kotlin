/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.test.transport

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.ConnectionSetupPayload
import io.rsocket.SocketAcceptor
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.ClientTransport
import io.rsocket.kotlin.util.AbstractRSocket
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.netty.server.CloseableChannel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoProcessor
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

typealias RSocketFactoryJava = io.rsocket.RSocketFactory
typealias RSocketJava = io.rsocket.RSocket
typealias AbstractRSocketJava = io.rsocket.AbstractRSocket
typealias PayloadJava = io.rsocket.Payload
typealias DefaultPayloadJava = io.rsocket.util.DefaultPayload

abstract class EndToEndTest
(private val clientTransport: (InetSocketAddress) -> ClientTransport,
 private val serverTransport: (InetSocketAddress) -> ServerTransport<CloseableChannel>) {
    private lateinit var server: CloseableChannel
    private lateinit var client: RSocket
    private lateinit var clientHandler: TestClientHandler
    private lateinit var serverHandler: TestServerHandler
    private val errors = Errors()

    @Before
    fun setUp() {
        val address = InetSocketAddress
                .createUnresolved("localhost", 0)
        val serverAcceptor = ServerAcceptor()
        clientHandler = TestClientHandler()

        server = RSocketFactoryJava
                .receive()
                .errorConsumer(errors.errorsConsumer())
                .acceptor (serverAcceptor)
                .transport(serverTransport(address))
                .start()
                .block(java.time.Duration.ofSeconds(5))!!

        client = RSocketFactory
                .connect()
                .errorConsumer(errors.errorsConsumer())
                .keepAlive {
                    it.keepAliveInterval(Duration.ofSeconds(42))
                            .keepAliveMaxLifeTime(Duration.ofMinutes(42))
                }
                .acceptor { { clientHandler } }
                .transport { clientTransport(server.address()) }
                .start()
                .blockingGet()

        serverHandler = serverAcceptor.handler().block(java.time.Duration.ofSeconds(5))!!
    }

    @After
    fun tearDown() {
        server.dispose()
        server.onClose().block(java.time.Duration.ofSeconds(5))
    }

    @Test
    fun fireAndForget() {
        val data = testData()
        client.fireAndForget(data.payload())
                .andThen(Completable.timer(1, TimeUnit.SECONDS))
                .blockingAwait(10, TimeUnit.SECONDS)
        assertThat(errors.errors()).isEmpty()
        assertThat(serverHandler.fireAndForgetData()).hasSize(1)
                .contains(data)
    }

    @Test
    open fun response() {
        val data = testData()
        val response = client.requestResponse(data.payload())
                .timeout(10, TimeUnit.SECONDS)
                .blockingGet()
        assertThat(errors.errors()).isEmpty()
        assertThat(Data(response)).isEqualTo(data)
    }

    @Test
    fun stream() {
        val data = testData()
        val response = client.requestStream(data.payload())
                .timeout(10, TimeUnit.SECONDS)
                .map { Data(it) }
                .toList()
                .blockingGet()
        assertThat(errors.errors()).isEmpty()
        assertThat(response).hasSize(1).contains(data)
    }

    @Test
    fun channel() {
        val data = testData()
        val response = client.requestChannel(Flowable.just(data.payload()))
                .timeout(10, TimeUnit.SECONDS)
                .toList()
                .blockingGet()
        assertThat(errors.errors()).isEmpty()
        assertThat(response).hasSize(1)
        assertThat(Data(response[0])).isEqualTo(data)
    }

    @Test
    fun clientMetadataPush() {
        val payload = DefaultPayload("", "md")
        client.metadataPush(payload)
                .andThen(Completable.timer(1, TimeUnit.SECONDS))
                .timeout(10, TimeUnit.SECONDS)
                .blockingAwait()

        assertThat(errors.errors()).isEmpty()
        assertThat(serverHandler.metadataPushData())
                .hasSize(1)
                .contains("md")
    }

    @Test
    fun serverMetadataPush() {
        val payload = DefaultPayloadJava.create("", "md")
        serverHandler.sendMetadataPush(payload)
                .then(Mono.delay(java.time.Duration.ofSeconds(1)))
                .block(java.time.Duration.ofSeconds(5))

        assertThat(errors.errors()).isEmpty()
        assertThat(clientHandler.metadataPushData())
                .hasSize(1)
                .contains("md")
    }

    @Test
    open fun close() {
        val success = client.close()
                .andThen(client.onClose())
                .blockingAwait(10, TimeUnit.SECONDS)
        if (!success) {
            throw IllegalStateException("RSocket.close() did not trigger RSocket.onClose()")
        }
    }

    @Test
    fun availability() {
        assertThat(client.availability())
                .isCloseTo(1.0, Offset.offset(1e-5))
    }

    @Test
    open fun closedAvailability() {
        client.close()
                .andThen(client.onClose())
                .blockingAwait(10,TimeUnit.SECONDS)

        assertThat(client.availability())
                .isCloseTo(0.0, Offset.offset(1e-5))
    }


    private fun testData() = Data("d", "md")

    internal class TestClientHandler(private val requester: RSocket? = null) : AbstractRSocket() {
        private val fnf = ArrayList<Data>()
        private val metadata = ArrayList<String>()

        override fun fireAndForget(payload: Payload): Completable {
            fnf += Data(payload)
            return Completable.complete()
        }

        override fun metadataPush(payload: Payload): Completable {
            metadata += payload.metadataUtf8
            return Completable.complete()
        }

        override fun requestResponse(payload: Payload): Single<Payload> {
            return Single.just(payload)
        }

        override fun requestStream(payload: Payload): Flowable<Payload> {
            return Flowable.just(payload)
        }

        override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
            return Flowable.fromPublisher(payloads)
        }

        fun metadataPushData() = metadata
    }

    internal class TestServerHandler(private val requester: RSocketJava? = null) : AbstractRSocketJava() {
        private val fnf = ArrayList<Data>()
        private val metadata = ArrayList<String>()

        override fun fireAndForget(payload: PayloadJava): Mono<Void> {
            fnf += Data(payload)
            return Mono.empty()
        }

        override fun metadataPush(payload: PayloadJava): Mono<Void> {
            metadata += payload.metadataUtf8
            return Mono.empty()
        }

        override fun requestResponse(payload: PayloadJava): Mono<PayloadJava> {
            return Mono.just(payload)
        }

        override fun requestStream(payload: PayloadJava): Flux<PayloadJava> {
            return Flux.just(payload)
        }

        override fun requestChannel(payloads: Publisher<PayloadJava>): Flux<PayloadJava> {
            return Flux.from(payloads)
        }

        fun sendMetadataPush(payload: PayloadJava): Mono<Void> = requester
                ?.metadataPush(payload)
                ?: Mono.empty()

        fun fireAndForgetData() = fnf

        fun metadataPushData() = metadata
    }

    internal class Errors {

        private val errors = ArrayList<Throwable>()

        fun errorsConsumer(): (Throwable) -> Unit = {
            errors += (it)
        }

        fun errors() = errors
    }

    internal class ServerAcceptor
        : SocketAcceptor {

        private val serverHandlerReady = MonoProcessor
                .create<TestServerHandler>()

        override fun accept(setup: ConnectionSetupPayload,
                            sendingSocket: RSocketJava): Mono<RSocketJava> {
            val handler = TestServerHandler(sendingSocket)
            serverHandlerReady.onNext(handler)
            return Mono.just(handler)
        }

        fun handler(): Mono<TestServerHandler> {
            return serverHandlerReady
        }
    }

    internal data class Data(val data: String, val metadata: String) {
        constructor(payload: Payload) : this(payload.dataUtf8, payload.metadataUtf8)
        constructor(payload: PayloadJava) : this(payload.dataUtf8, payload.metadataUtf8)

        fun payload(): Payload = DefaultPayload(data, metadata)
    }
}