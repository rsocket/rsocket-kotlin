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

package io.rsocket.kotlin.test

import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.*
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.WebsocketServerTransport
import io.rsocket.transport.okhttp.client.OkhttpWebsocketClientTransport
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit
import kotlin.math.max

typealias RSocketFactoryJava = io.rsocket.RSocketFactory
typealias AbstractRSocketJava = io.rsocket.AbstractRSocket
typealias PayloadJava = io.rsocket.Payload
typealias DefaultPayloadJava = io.rsocket.util.DefaultPayload

class InteractionsStressTest {
    private lateinit var server: CloseableChannel
    private lateinit var client: RSocket
    private lateinit var testHandler: TestHandler
    @Before
    fun setUp() {
        val serverTransport = WebsocketServerTransport.create("localhost", 0)
        testHandler = TestHandler()
        server = RSocketFactoryJava
                .receive()
                .acceptor { _, _ -> Mono.just(testHandler) }
                .transport(serverTransport)
                .start()
                .block(java.time.Duration.ofSeconds(5))!!

        val address = server.address()
        val clientTransport = OkhttpWebsocketClientTransport
                .create(HttpUrl.Builder()
                        .scheme("http")
                        .host(address.hostName)
                        .port(address.port)
                        .build())

        client = RSocketFactory
                .connect()
                .keepAlive {
                    it.keepAliveInterval(Duration.ofSeconds(42))
                            .keepAliveMaxLifeTime(Duration.ofMinutes(1))
                }
                .transport { clientTransport }
                .start()
                .blockingGet()
    }

    @After
    fun tearDown() {
        server.dispose()
        server.onClose().block(java.time.Duration.ofSeconds(5))
    }

    @Test
    fun response() {
        interaction(
                { payload -> payload.matches("response") },
                {
                    it.flatMapSingle( { num ->
                        client.requestResponse(
                                DefaultPayload.text("response$num"))
                    }, false, interactionConcurrency)
                })
    }

    @Test
    fun stream() {
        interaction(
                { payload -> payload.matches("stream") },
                {
                    it.flatMap ({ num ->
                        client.requestStream(
                                DefaultPayload.text("stream$num"))
                    }, false, interactionConcurrency)
                })
    }

    @Test
    fun channel() {
        interaction(
                { payload -> payload.matches("channel") },
                {
                    it.flatMap ({ num ->
                        client.requestChannel(
                                Flowable.just(DefaultPayload.text("channel$num")))
                    },false, interactionConcurrency)
                })
    }

    private fun interaction(pred: (Payload) -> Boolean,
                            interaction: (Flowable<Long>) -> Flowable<Payload>) =
            interaction(testDuration, pred, interaction)

    private fun interaction(durationSeconds: Long,
                            pred: (Payload) -> Boolean,
                            interaction: (Flowable<Long>) -> Flowable<Payload>) {

        val errors = UnicastProcessor.create<Long>()
        val disposable = CompositeDisposable()
        repeat(interactionCount) {
            disposable += interaction(source()).timeout(1, TimeUnit.SECONDS)
                    .subscribe({ res ->
                        if (!pred(res)) {
                            errors.onError(
                                    IllegalStateException("Unexpected message" +
                                            " contents: ${res.dataUtf8}"))
                        }
                    }, { err -> errors.onError(err) })
        }

        val delay = Flowable
                .timer(durationSeconds, TimeUnit.SECONDS)

        delay.ambWith(errors).ignoreElements()
                .doFinally { disposable.dispose() }
                .blockingAwait()
    }

    private fun Payload.matches(str: String): Boolean {
        val data = dataUtf8
        return data.startsWith(str) &&
                data.substringAfter(str).toLong() >= 0

    }

    internal class TestHandler
        : AbstractRSocketJava() {

        override fun requestResponse(payload: PayloadJava): Mono<PayloadJava> =
                Mono.just(payload)

        override fun requestStream(payload: PayloadJava): Flux<PayloadJava> {
            val data = payload.dataUtf8
            return Flux.just(
                    DefaultPayloadJava.create(data),
                    DefaultPayloadJava.create(data))
        }

        override fun requestChannel(payloads: Publisher<PayloadJava>): Flux<PayloadJava> {
            return Flux.from(payloads).flatMap { payload ->
                val data = payload.dataUtf8
                Flux.just(
                        DefaultPayloadJava.create(data),
                        DefaultPayloadJava.create(data))
            }
        }
    }

    private operator fun CompositeDisposable.plusAssign(d: Disposable) {
        add(d)
    }

    companion object {
        private fun source() =
                Flowable.interval(100, TimeUnit.MICROSECONDS)
                        .onBackpressureDrop()

        private const val testDuration = 20L

        private val interactionCount = max(1, Runtime.getRuntime().availableProcessors() / 2)

        private const val interactionConcurrency = 4
    }
}