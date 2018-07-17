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

package io.rsocket.kotlin.internal

import io.reactivex.Flowable
import io.reactivex.subscribers.TestSubscriber
import io.rsocket.kotlin.Payload
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class StreamReceiverTest {
    lateinit var receiver: StreamReceiver
    lateinit var subs: TestSubscriber<Payload>
    @Before
    fun setUp() {
        receiver = StreamReceiver.create()
        subs = TestSubscriber.create<Payload>(0)
    }

    @Test
    fun requestAfterError() {
        request { receiver.onError(RuntimeException()) }
    }

    @Test
    fun requestAfterComplete() {
        request { receiver.onComplete() }
    }

    @Test
    fun requestAfterCancel() {
        request { subs.cancel() }
    }

    fun request(f: () -> Unit) {
        val expectedEqs = arrayListOf(100L, 200L, 300L, 400L)
        val actualReqs = ArrayList<Long>()
        val untilIndex = 1
        val until = expectedEqs[untilIndex]

        receiver.doOnRequestIfActive { actualReqs += it }.subscribe(subs)
        val ticks = Flowable.fromIterable(expectedEqs)
                .flatMap { Flowable.timer(it, TimeUnit.MILLISECONDS).map { _ -> it } }
                .share()

        ticks.take(expectedEqs.size.toLong() - 1)
                .doOnNext {
                    if (it == until)
                        f()
                    else
                        subs.request(it)
                }.subscribe()

        ticks.ignoreElements().blockingAwait()
        assertEquals(untilIndex, actualReqs.size)
        for (i in 0 until untilIndex) {
            assertEquals(expectedEqs[i], actualReqs[i])
        }
    }
}