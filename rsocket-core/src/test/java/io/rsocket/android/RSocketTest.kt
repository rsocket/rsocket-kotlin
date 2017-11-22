/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.rsocket.android

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.PublishProcessor
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.android.test.util.LocalDuplexConnection
import io.rsocket.android.util.PayloadImpl
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.reactivestreams.Publisher
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RSocketTest {

    @get:Rule
    val rule = SocketRule()

    @Test(timeout = 2000)
    fun testRequestReplyNoError() {
        val subscriber = TestSubscriber.create<Payload>()
        rule.crs.requestResponse(PayloadImpl("hello")).toFlowable().blockingSubscribe(subscriber)
        assertThat("unexpected errors", subscriber.errorCount(), `is`(0))
        assertThat("unexpected payloads", subscriber.valueCount(), `is`(1))
        assertThat("unexpected completions", subscriber.completions(), `is`(1L))
        rule.assertNoErrors()
    }

    @Test(timeout = 2000)
    fun testHandlerEmitsError() {
        rule.setRequestAcceptor(
                object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Single<Payload> =
                            Single.error<Payload>(NullPointerException("Deliberate exception."))
                                    .delay(100, TimeUnit.MILLISECONDS)
                })
        val subscriber = TestSubscriber.create<Payload>()
        rule.crs.requestResponse(PayloadImpl.EMPTY).toFlowable().blockingSubscribe(subscriber)
        assertThat("unexpected frames", subscriber.errorCount(), `is`(1))
        assertThat("unexpected frames", subscriber.valueCount(), `is`(0))
        assertThat("unexpected frames", subscriber.completions(), `is`(0L))

        rule.assertNoClientErrors()
        rule.assertServerErrorCount(1)

    }

    @Test(timeout = 2000)
    @Throws(Exception::class)
    fun testChannel() {
        val latch = CountDownLatch(10)
        val requests = Flowable.range(0, 10).map<Payload> { i -> PayloadImpl("streaming in -> " + i) }

        val responses = rule.crs.requestChannel(requests)

        responses.doOnNext { latch.countDown() }.subscribe()

        latch.await()
    }

    class SocketRule : ExternalResource() {

        lateinit internal var crs: RSocketClient
        internal lateinit var srs: RSocketServer
        private var requestAcceptor: RSocket? = null
        lateinit private var serverProcessor: PublishProcessor<Frame>
        lateinit private var clientProcessor: PublishProcessor<Frame>
        private val clientErrors = ArrayList<Throwable>()
        private val serverErrors = ArrayList<Throwable>()

        override fun apply(base: Statement, description: Description?): Statement {
            return object : Statement() {
                @Throws(Throwable::class)
                override fun evaluate() {
                    init()
                    base.evaluate()
                }
            }
        }

        private fun init() {
            serverProcessor = PublishProcessor.create()
            clientProcessor = PublishProcessor.create()

            val serverConnection = LocalDuplexConnection("server", clientProcessor, serverProcessor)
            val clientConnection = LocalDuplexConnection("client", serverProcessor, clientProcessor)

            requestAcceptor = if (null != requestAcceptor)
                requestAcceptor
            else
                object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Single<Payload> = Single.just(payload)
                            .delay(100, TimeUnit.MILLISECONDS)

                    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
                        Flowable.fromPublisher(payloads)
                                .map { payload -> PayloadImpl("server got -> [$payload]") }
                                .subscribe()

                        return Flowable.range(1, 10)
                                .map { payload -> PayloadImpl("server got -> [$payload]") }
                    }
                }

            srs = RSocketServer(
                    serverConnection, requestAcceptor!!) { throwable -> serverErrors.add(throwable) }

            crs = RSocketClient(
                    clientConnection,
                    { throwable -> clientErrors.add(throwable) },
                    StreamIdSupplier.clientSupplier())
        }

        fun setRequestAcceptor(requestAcceptor: RSocket) {
            this.requestAcceptor = requestAcceptor
            close(srs, crs)
            init()
        }

        private fun close(socket: RSocket) {
            socket.close().subscribe()
            socket.onClose().blockingAwait()
        }

        private fun close(vararg sockets: RSocket) = sockets.forEach { close(it) }

        fun assertNoErrors() {
            assertNoClientErrors()
            assertNoServerErrors()
        }

        fun assertNoClientErrors() {
            assertThat(
                    "Unexpected error on the client connection.",
                    clientErrors,
                    empty())
        }

        fun assertNoServerErrors() {
            assertThat(
                    "Unexpected error on the server connection.",
                    serverErrors,
                    empty())
        }

        fun assertServerErrorCount(count: Int) {
            assertThat("Unexpected error count on the server connection.",
                    serverErrors,
                    hasSize(count))
        }
    }
}
