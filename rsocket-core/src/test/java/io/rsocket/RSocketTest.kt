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
 *//*


package io.rsocket

import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.`is`
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify

import io.rsocket.exceptions.ApplicationException
import io.rsocket.test.util.LocalDuplexConnection
import io.rsocket.test.util.TestSubscriber
import io.rsocket.util.PayloadImpl
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import org.hamcrest.MatcherAssert
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class RSocketTest {

    @Rule @JvmField
    val rule = SocketRule()

    @Test(timeout = 2000)
    fun testRequestReplyNoError() {
        val subscriber = TestSubscriber.create<Payload>()
        rule.crs!!.requestResponse(PayloadImpl("hello")).subscribe(subscriber)
        verify(subscriber)!!.onNext(TestSubscriber.anyPayload())
        verify(subscriber)!!.onComplete()
        rule.assertNoErrors()
    }

    @Test(timeout = 2000)
    @Ignore
    fun testHandlerEmitsError() {
        rule.setRequestAcceptor(
                object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Mono<Payload> {
                        return Mono.error(NullPointerException("Deliberate exception."))
                    }
                })
        val subscriber = TestSubscriber.create<Payload>()
        rule.crs!!.requestResponse(PayloadImpl.EMPTY).subscribe(subscriber)
        verify(subscriber)!!.onError(any(ApplicationException::class.java))
        rule.assertNoErrors()
    }

    @Test(timeout = 2000)
    @Throws(Exception::class)
    fun testChannel() {
        val latch = CountDownLatch(10)
        val requests = Flux.range(0, 10).map<Payload> { i -> PayloadImpl("streaming in -> " + i!!) }

        val responses = rule.crs!!.requestChannel(requests)

        responses.doOnNext { p -> latch.countDown() }.subscribe()

        latch.await()
    }

    class SocketRule : ExternalResource() {

        internal var crs: RSocketClient? = null
        private var srs: RSocketServer? = null
        private var requestAcceptor: RSocket? = null
        lateinit private var serverProcessor: DirectProcessor<Frame>
        lateinit private var clientProcessor: DirectProcessor<Frame>
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

        protected fun init() {
            serverProcessor = DirectProcessor.create()
            clientProcessor = DirectProcessor.create()

            val serverConnection = LocalDuplexConnection("server", clientProcessor, serverProcessor)
            val clientConnection = LocalDuplexConnection("client", serverProcessor, clientProcessor)

            requestAcceptor = if (null != requestAcceptor)
                requestAcceptor
            else
                object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Mono<Payload> {
                        return Mono.just(payload)
                    }

                    override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> {
                        Flux.from(payloads)
                                .map<Any> { payload -> PayloadImpl("server got -> [" + payload.toString() + "]") }
                                .subscribe()

                        return Flux.range(1, 10)
                                .map { payload -> PayloadImpl("server got -> [" + payload!!.toString() + "]") }
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
            init()
        }

        fun assertNoErrors() {
            MatcherAssert.assertThat(
                    "Unexpected error on the client connection.", clientErrors, `is`(empty<Any>()))
            MatcherAssert.assertThat(
                    "Unexpected error on the server connection.", serverErrors, `is`(empty<Any>()))
        }
    }
}
*/
