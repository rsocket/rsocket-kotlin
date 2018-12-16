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

package io.rsocket.kotlin

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.internal.observers.BlockingMultiObserver
import io.reactivex.processors.PublishProcessor
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.kotlin.exceptions.ApplicationException
import io.rsocket.kotlin.internal.ClientStreamIds
import io.rsocket.kotlin.internal.RSocketRequester
import io.rsocket.kotlin.internal.frame.RequestFrameFlyweight
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

class RSocketRequesterTest {

    @get:Rule
    val rule = ClientSocketRule()

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
        val stream = rule.requester.requestStream(DefaultPayload.EMPTY)
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
    fun testHandleApplicationException() {
        val response = rule.requester.requestResponse(DefaultPayload.EMPTY).toFlowable()
        val responseSub = TestSubscriber.create<Payload>()
        response.subscribe(responseSub)
        rule.receiver.onNext(Frame.Error.from(1, ApplicationException("error")))

        assertThat("expected App error frame", responseSub.errorCount(), `is`(1))
    }

    @Test(timeout = 2000)
    fun testHandleValidFrame() {
        val response = rule.requester.requestResponse(DefaultPayload.EMPTY).toFlowable()
        val sub = TestSubscriber.create<Payload>()
        response.subscribe(sub)

        rule.receiver.onNext(
                Frame.PayloadFrame.from(
                        1, FrameType.NEXT_COMPLETE, DefaultPayload.EMPTY))
        sub.assertValueCount(1)
        sub.assertComplete()
    }

    @Test(timeout = 2000)
    fun testRequestReplyWithCancel() {
        val subs = TestSubscriber.create<Frame>()
        rule.sender.filter { it.type != FrameType.KEEPALIVE }
                .subscribe(subs)
        rule.requester.requestResponse(DefaultPayload.EMPTY).timeout(100, TimeUnit.MILLISECONDS)
                .onErrorReturnItem(DefaultPayload("test"))
                .blockingGet()

        val sent = subs.values()
        assertThat(
                "Unexpected sent frames size", sent, hasSize(2))
        assertThat(
                "Unexpected frame sent on the connection.", sent[0].type, `is`<FrameType>(FrameType.REQUEST_RESPONSE))
        assertThat("Unexpected frame sent on the connection.", sent[1].type, `is`<FrameType>(FrameType.CANCEL))
    }

    @Test
    fun testLazyRequestResponse() {

        val response = rule.requester.requestResponse(DefaultPayload.EMPTY).toFlowable()

        val framesSubs = TestSubscriber.create<Frame>()
        rule.sender.filter { it.type != FrameType.KEEPALIVE }.subscribe(framesSubs)

        response.subscribe()
        response.subscribe()

        val streamId1 = framesSubs.values()[0].streamId
        val streamId2 = framesSubs.values()[1].streamId

        assertThat("Stream ID reused.", streamId2, not(equalTo(streamId1)))
    }

    @Test
    fun testChannelResponseIsReceivedAfterRequestCancellation() {
        val subs = TestSubscriber<Payload>()
        rule.requester.requestChannel(Flowable.just(DefaultPayload.EMPTY))
                .subscribe(subs)

        val prevRequestStreamId = 1
        rule.receiver.onNext(Frame.Cancel.from(prevRequestStreamId))
        rule.receiver.onNext(Frame.Error.from(prevRequestStreamId, RuntimeException("expected_error")))

        subs.assertValueCount(0)
                .assertError { e -> e is ApplicationException && "expected_error" == e.message }
    }

    @Test(timeout = 3_000)
    fun requestErrorOnConnectionClose() {
        Completable.timer(100, TimeUnit.MILLISECONDS)
                .andThen(rule.conn.close()).subscribe()
        val requestStream = rule.requester.requestStream(DefaultPayload("test"))
        val subs = TestSubscriber.create<Payload>()
        requestStream.blockingSubscribe(subs)
        subs.assertNoValues()
        subs.assertError { it is ClosedChannelException }
    }

    @Test(timeout = 5_000)
    fun streamErrorAfterConnectionClose() {
        assertFlowableError { it.requestStream(DefaultPayload("test")) }
    }

    @Test(timeout = 5_000)
    fun reqStreamErrorAfterConnectionClose() {
        assertFlowableError { it.requestStream(DefaultPayload("test")) }
    }

    @Test(timeout = 5_000)
    fun reqChannelErrorAfterConnectionClose() {
        assertFlowableError { it.requestChannel(Flowable.just(DefaultPayload("test"))) }
    }

    @Test(timeout = 5_000)
    fun reqResponseErrorAfterConnectionClose() {
        assertSingleError { it.requestResponse(DefaultPayload("test")) }
    }

    @Test(timeout = 5_000)
    fun fnfErrorAfterConnectionClose() {
        assertCompletableError { it.fireAndForget(DefaultPayload("test")) }
    }

    @Test(timeout = 5_000)
    fun metadataPushAfterConnectionClose() {
        assertCompletableError { it.metadataPush(DefaultPayload("test")) }
    }

    private fun assertFlowableError(f: (RSocket) -> Flowable<Payload>) {
        rule.conn.close().subscribe()
        val subs = TestSubscriber.create<Payload>()
        f(rule.requester).delaySubscription(100, TimeUnit.MILLISECONDS).blockingSubscribe(subs)
        subs.assertNoValues()
        subs.assertError { it is ClosedChannelException }
    }

    private fun assertCompletableError(f: (RSocket) -> Completable) {
        rule.conn.close().subscribe()
        val requestStream = Completable
                .timer(100, TimeUnit.MILLISECONDS)
                .andThen(f(rule.requester))
        val err = requestStream.blockingGet()
        assertThat("error is not ClosedChannelException",
                err is ClosedChannelException)
    }

    private fun assertSingleError(f: (RSocket) -> Single<Payload>) {
        rule.conn.close().subscribe()
        val response = f(rule.requester).delaySubscription(100, TimeUnit.MILLISECONDS)
        val subs = BlockingMultiObserver<Payload>()
        response.subscribe(subs)
        val err = subs.blockingGetError()
        assertThat("error is not ClosedChannelException", err is ClosedChannelException)
    }


    class ClientSocketRule : ExternalResource() {
        lateinit var sender: PublishProcessor<Frame>
        lateinit var receiver: PublishProcessor<Frame>
        lateinit var conn: LocalDuplexConnection
        internal lateinit var requester: RSocketRequester
        val errors: MutableList<Throwable> = ArrayList()

        override fun apply(base: Statement, description: Description?): Statement {
            return object : Statement() {
                @Throws(Throwable::class)
                override fun evaluate() {
                    sender = PublishProcessor.create<Frame>()
                    receiver = PublishProcessor.create<Frame>()
                    conn = LocalDuplexConnection("clientRequesterConn", sender, receiver)

                    requester = RSocketRequester(
                            conn,
                            { throwable ->
                                errors.add(throwable)
                                Unit
                            },
                            ClientStreamIds(),
                            streamWindow)

                    base.evaluate()
                }
            }
        }

        companion object {
            val streamWindow = 20
        }
    }
}

