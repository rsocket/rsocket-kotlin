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

package io.rsocket.kotlin

import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.rsocket.kotlin.internal.ClientStreamIds
import io.rsocket.kotlin.internal.RSocketRequester
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

class RequesterStreamWindowTest {

    @get:Rule
    val rule = WindowRSocketRule()

    @Test(timeout = 3_000)
    fun requesterStreamInbound() {
        checkRequesterInbound(
                rule.requester.requestStream(DefaultPayload("test")),
                FrameType.REQUEST_STREAM)
    }

    @Test(timeout = 3_000)
    fun requesterChannelInbound() {
        checkRequesterInbound(
                rule.requester.requestChannel(Flowable.just(DefaultPayload("test"))),
                FrameType.REQUEST_CHANNEL)
    }

    @Test(timeout = 3_000)
    fun requesterChannelOutbound() {
        var demand = -1L
        val request = Flowable.just(1, 2, 3)
                .map { DefaultPayload(it.toString()) as Payload }
                .doOnRequest { demand = it }
        rule.requester.requestChannel(request).subscribe({}, {})
        rule.receiver.onNext(Frame.RequestN.from(1, Int.MAX_VALUE))
        assertThat("requesterConnection channel handler is not limited",
                demand,
                equalTo(WindowRSocketRule.streamWindow.toLong()))
    }

    private fun checkRequesterInbound(f: Flowable<Payload>, type: FrameType) {
        f.delaySubscription(100, TimeUnit.MILLISECONDS).subscribe({}, {})

        val reqN = rule.sender.filter { it.type == type }
                .firstOrError().blockingGet()

        assertThat(
                "request N is not limited",
                Frame.Request.initialRequestN(reqN),
                equalTo(WindowRSocketRule.streamWindow))

    }

    class WindowRSocketRule : ExternalResource() {
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
                    conn = LocalDuplexConnection("conn", sender, receiver)

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