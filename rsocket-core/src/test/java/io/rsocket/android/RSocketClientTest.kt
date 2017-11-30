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

import io.reactivex.Completable
import io.reactivex.processors.PublishProcessor
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.android.exceptions.ApplicationException
import io.rsocket.android.exceptions.RejectedSetupException
import io.rsocket.android.frame.RequestFrameFlyweight
import io.rsocket.android.test.util.LocalDuplexConnection
import io.rsocket.android.util.PayloadImpl
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

class RSocketClientTest {

    @get:Rule
    val rule = ClientSocketRule()

    @Test(timeout = 2000)
    @Throws(Exception::class)
    fun testKeepAlive() {
        val actualFrame = rule.sender.blockingFirst()

        assertThat("Unexpected frame sent.", actualFrame.type, `is`<FrameType>(FrameType.KEEPALIVE))
        assertThat("errors not empty", rule.errors, hasSize(0))
    }

    @Test(timeout = 2000)
    fun testInvalidFrameOnStream0() {

        rule.receiver.onNext(Frame.RequestN.from(0, 10))
        val errors = rule.errors

        assertThat("Unexpected errors.",
                errors,
                hasSize<Throwable>(1))
        assertThat(
                "Unexpected error received.",
                errors,
                contains(instanceOf<Throwable>(IllegalStateException::class.java)))
    }

    @Test(timeout = 2000)
    fun testStreamInitialN() {
        val stream = rule.client.requestStream(PayloadImpl.EMPTY)
        Completable.timer(100, TimeUnit.MILLISECONDS)
                .subscribe({
                    val subscriber = TestSubscriber<Payload>()
                    stream.subscribe(subscriber)
                    subscriber.request(5)
                })

        val sent = rule.sender
                .filter { f -> f.type != FrameType.KEEPALIVE }
                .take(1)
                .toList().blockingGet()

        assertThat("sent frame count", sent.size, `is`(1))

        val f = sent[0]

        assertThat("initial frame",
                f.type,
                `is`<FrameType>(FrameType.REQUEST_STREAM))
        assertThat("initial request n",
                RequestFrameFlyweight.initialRequestN(f.content()),
                equalTo(ClientSocketRule.streamWindow))
    }

    @Test(timeout = 2000)
    fun testHandleSetupException() {
        rule.receiver.onNext(Frame.Error.from(0, RejectedSetupException("boom")))

        val errors = rule.errors
        assertThat("Unexpected errors.", errors, hasSize<Throwable>(1))
        assertThat(
                "Unexpected error received.",
                errors,
                contains(instanceOf<Throwable>(RejectedSetupException::class.java)))
    }

    @Test(timeout = 2000)
    fun testHandleApplicationException() {
        val response = rule.client.requestResponse(PayloadImpl.EMPTY).toFlowable()
        val responseSub = TestSubscriber.create<Payload>()
        response.subscribe(responseSub)
        rule.receiver.onNext(Frame.Error.from(1, ApplicationException("error")))

        assertThat("expected App error frame", responseSub.errorCount(), `is`(1))
    }

    @Test(timeout = 2000)
    fun testHandleValidFrame() {
        val response = rule.client.requestResponse(PayloadImpl.EMPTY).toFlowable()
        val sub = TestSubscriber.create<Payload>()
        response.subscribe(sub)

        rule.receiver.onNext(
                Frame.PayloadFrame.from(
                        1, FrameType.NEXT_COMPLETE, PayloadImpl.EMPTY))
        sub.assertValueCount(1)
        sub.assertComplete()
    }

    @Test(timeout = 2000)
    fun testRequestReplyWithCancel() {
        val subs = TestSubscriber.create<Frame>()
        rule.sender.filter { it.type != FrameType.KEEPALIVE }
                .subscribe(subs)
        rule.client.requestResponse(PayloadImpl.EMPTY).timeout(100, TimeUnit.MILLISECONDS)
                .onErrorReturnItem(PayloadImpl("test"))
                .blockingGet()

        val sent = subs.values()
        assertThat(
                "Unexpected sent frames size", sent, hasSize(2))
        assertThat(
                "Unexpected frame sent on the connection.", sent[0].type, `is`<FrameType>(FrameType.REQUEST_RESPONSE))
        assertThat("Unexpected frame sent on the connection.", sent[1].type, `is`<FrameType>(FrameType.CANCEL))
    }

    @Test(timeout = 2000)
    @Ignore
    fun testRequestReplyErrorOnSend() {

        rule.conn.setAvailability(0.0) // Fails send
        val response = rule.client.requestResponse(PayloadImpl.EMPTY).toFlowable()
        val responseSub = TestSubscriber.create<Payload>()
        response.subscribe(responseSub)
        assertThat("expected connection error", responseSub.errorCount(), `is`(1))
    }

    @Test
    fun testLazyRequestResponse() {

        val response = rule.client.requestResponse(PayloadImpl.EMPTY).toFlowable()

        val framesSubs = TestSubscriber.create<Frame>()
        rule.sender.filter { it.type != FrameType.KEEPALIVE }.subscribe(framesSubs)

        response.subscribe()
        response.subscribe()

        val streamId1 = framesSubs.values()[0].streamId
        val streamId2 = framesSubs.values()[1].streamId

        assertThat("Stream ID reused.", streamId2, not(equalTo(streamId1)))
    }

    class ClientSocketRule : ExternalResource() {
        lateinit var sender: PublishProcessor<Frame>
        lateinit var receiver: PublishProcessor<Frame>
        lateinit var conn: LocalDuplexConnection
        internal lateinit var client: RSocketClient
        val errors: MutableList<Throwable> = ArrayList()

        override fun apply(base: Statement, description: Description?): Statement {
            return object : Statement() {
                @Throws(Throwable::class)
                override fun evaluate() {
                    sender = PublishProcessor.create<Frame>()
                    receiver = PublishProcessor.create<Frame>()
                    conn = LocalDuplexConnection("clientRequesterConn", sender, receiver)

                    client = RSocketClient(
                            conn,
                            { throwable ->
                                errors.add(throwable)
                                Unit
                            },
                            StreamIdSupplier.clientSupplier(),
                            streamWindow,
                            Duration(100, TimeUnit.MILLISECONDS),
                            Duration(100, TimeUnit.MILLISECONDS),
                            4)

                    base.evaluate()
                }
            }
        }

        companion object {
            val streamWindow = 20
        }
    }
}

