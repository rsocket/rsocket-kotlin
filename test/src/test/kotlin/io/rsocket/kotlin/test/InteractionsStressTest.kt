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
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.UnicastProcessor
import io.reactivex.schedulers.Schedulers
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.NettyContextCloseable
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport
import io.rsocket.kotlin.util.AbstractRSocket
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class InteractionsStressTest {
    private lateinit var server: NettyContextCloseable
    private lateinit var client: RSocket
    private lateinit var testHandler: TestHandler
    @Before
    fun setUp() {
        val address = InetSocketAddress
                .createUnresolved("localhost", 0)
        val serverTransport = TcpServerTransport.create(address)
        testHandler = TestHandler()
        server = RSocketFactory
                .receive()
                .acceptor { { _, _ -> Single.just(testHandler) } }
                .transport(serverTransport)
                .start()
                .blockingGet()

        val clientTransport = TcpClientTransport
                .create(server.address())

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
        server.close().andThen(server.onClose()).blockingAwait()
    }

    @Test
    fun response() {
        interaction(
                { payload -> payload.matches("response") },
                {
                    it.flatMapSingle { num ->
                        client.requestResponse(
                                DefaultPayload.text("response$num"))
                    }
                })
    }

    @Test
    fun stream() {
        interaction(
                { payload -> payload.matches("stream") },
                {
                    it.flatMap { num ->
                        client.requestStream(
                                DefaultPayload.text("stream$num"))
                    }
                })
    }

    @Test
    fun channel() {
        interaction(
                { payload -> payload.matches("channel") },
                {
                    it.flatMap { num ->
                        client.requestChannel(
                                Flowable.just(DefaultPayload.text("channel$num")))
                    }
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
        repeat(threadsNum()) {
            disposable += interaction(source().observeOn(Schedulers.io()))
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

    private fun threadsNum() = Runtime.getRuntime().availableProcessors()

    internal class TestHandler
        : AbstractRSocket() {

        override fun requestResponse(payload: Payload): Single<Payload> =
                Single.just(payload)

        override fun requestStream(payload: Payload): Flowable<Payload> =
                Flowable.just(
                        DefaultPayload.text(payload.dataUtf8),
                        DefaultPayload.text(payload.dataUtf8))

        override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
            return Flowable.fromPublisher(payloads).flatMap { payload ->
                Flowable.just(
                        DefaultPayload.text(payload.dataUtf8),
                        DefaultPayload.text(payload.dataUtf8))
            }
        }
    }

    private operator fun CompositeDisposable.plusAssign(d: Disposable) {
        add(d)
    }

    companion object {
        private fun source() =
                Flowable.interval(intervalMillis, TimeUnit.MICROSECONDS)
                        .onBackpressureDrop()

        private const val intervalMillis: Long = 100

        private const val testDuration = 20L
    }
}