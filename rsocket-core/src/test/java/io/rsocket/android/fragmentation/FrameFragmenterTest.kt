/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.android.fragmentation

import io.reactivex.subscribers.TestSubscriber
import io.rsocket.Frame
import io.rsocket.FrameType
import io.rsocket.android.fragmentation.FrameFragmenter
import io.rsocket.android.util.PayloadImpl
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

class FrameFragmenterTest {
    @Test
    fun testFragmentWithMetadataAndData() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)
        val subs = TestSubscriber.create<Frame>()
        frameFragmenter.fragment(from).blockingSubscribe(subs)
        subs.assertValueCount(16).assertComplete().assertNoErrors()
    }

    @Test
    fun testFragmentWithMetadataAndDataWithOddData() {
        val data = createRandomBytes(17)
        val metadata = createRandomBytes(17)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)
        val subs = TestSubscriber.create<Frame>()
        frameFragmenter.fragment(from).blockingSubscribe(subs)
        subs.assertValueCount(17).assertComplete().assertNoErrors()
    }

    @Test
    fun testFragmentWithMetadataOnly() {
        val data = ByteBuffer.allocate(0)
        val metadata = createRandomBytes(16)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)
        val subs = TestSubscriber.create<Frame>()
        frameFragmenter.fragment(from).blockingSubscribe(subs)
        subs.assertValueCount(8).assertComplete().assertNoErrors()
    }

    @Test
    fun testFragmentWithDataOnly() {
        val data = createRandomBytes(16)
        val metadata = ByteBuffer.allocate(0)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)
        val subs = TestSubscriber.create<Frame>()
        frameFragmenter.fragment(from).blockingSubscribe(subs)
        subs.assertValueCount(8).assertComplete().assertNoErrors()
    }

    private fun createRandomBytes(size: Int): ByteBuffer {
        val bytes = ByteArray(size)
        ThreadLocalRandom.current().nextBytes(bytes)
        return ByteBuffer.wrap(bytes)
    }
}
