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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RSocketResponderTest {

    @get:Rule
    val rule = ServerSocketRule()

    @Test(timeout = 2000)
    @Throws(Exception::class)
    fun testHandleResponseFrameNoError() {
        Completable.timer(100, TimeUnit.MILLISECONDS).subscribe {
            val streamId = 4
            rule.sendRequest(streamId, FrameType.REQUEST_RESPONSE)
        }
        val subs = TestSubscriber.create<Frame>()
        rule.sender.take(1).blockingSubscribe(subs)

        assertThat("Unexpected error.", rule.errors, empty())
        assertThat(
                "Unexpected frame sent.",
                subs.values()[0].type,
                anyOf(`is`(FrameType.COMPLETE), `is`(FrameType.NEXT_COMPLETE)))
    }

    @Test(timeout = 2000)
    fun testHandlerEmitsError() {
        Completable.timer(100, TimeUnit.MILLISECONDS).subscribe {
            val streamId = 4
            rule.sendRequest(streamId, FrameType.REQUEST_STREAM)
        }
        val frame = rule.sender.blockingFirst()
        assertThat("Unexpected error.", rule.errors, `is`(empty<Any>()))
        assertThat(
                "Unexpected frame sent.", frame.type, `is`(FrameType.ERROR))
    }

    @Test(timeout = 10_000)
    fun testCancel() {
        val streamId = 4
        val cancelled = AtomicBoolean()
        rule.setAccSocket(
                object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Single<Payload> =
                            Single.never<Payload>().doOnDispose { cancelled.set(true) }
                })


        val beforeSubs = TestSubscriber.create<Frame>()
        Completable.timer(100, TimeUnit.MILLISECONDS).subscribe {
            rule.sendRequest(streamId, FrameType.REQUEST_RESPONSE)
        }

        rule.sender.timeout(1000, TimeUnit.MILLISECONDS, Flowable.empty()).blockingSubscribe(beforeSubs)

        assertThat("Unexpected error.", rule.errors, empty())
        assertThat("Unexpected frame sent.", beforeSubs.valueCount(), `is`(0))
        assertThat("Unexpected frame sent.", beforeSubs.errorCount(), `is`(0))

        Completable.timer(100, TimeUnit.MILLISECONDS).subscribe {
            rule.receiver.onNext(Frame.Cancel.from(streamId))
        }

        val afterSubs = TestSubscriber.create<Frame>()
        rule.sender.timeout(1000, TimeUnit.MILLISECONDS, Flowable.empty()).blockingSubscribe(afterSubs)

        assertThat("Unexpected frame sent.", afterSubs.valueCount(), `is`(0))
        assertThat("Unexpected frame sent.", afterSubs.errorCount(), `is`(0))
        assertThat("Subscription not cancelled.", cancelled.get(), `is`(true))
    }

    class ServerSocketRule : ExternalResource() {

        var acceptingSocket: RSocket = object : AbstractRSocket() {
            override fun requestResponse(payload: Payload): Single<Payload> = Single.just(payload)
        }

        lateinit var sender: PublishProcessor<Frame>
        lateinit var receiver: PublishProcessor<Frame>
        private lateinit var conn: LocalDuplexConnection
        lateinit var errors: MutableList<Throwable>
        internal lateinit var rsocket: RSocketResponder

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
            sender = PublishProcessor.create<Frame>()
            receiver = PublishProcessor.create<Frame>()
            conn = LocalDuplexConnection("serverConn", sender, receiver)
            errors = ArrayList()
            rsocket = RSocketResponder(
                    conn,
                    acceptingSocket,
                    { throwable -> errors.add(throwable) },
                    128)
        }

        fun setAccSocket(acceptingSocket: RSocket) {
            this.acceptingSocket = acceptingSocket
            acceptingSocket.close().subscribe()
            acceptingSocket.onClose().blockingAwait()
            init()
        }

        fun sendRequest(streamId: Int, frameType: FrameType) {
            val request = Frame.Request.from(streamId, frameType, PayloadImpl.EMPTY, 1)
            receiver.onNext(request)
            receiver.onNext(Frame.RequestN.from(streamId, 2))
        }
    }
}

