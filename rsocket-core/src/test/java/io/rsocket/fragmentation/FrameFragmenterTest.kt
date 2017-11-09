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

package io.rsocket.fragmentation

import io.rsocket.Frame
import io.rsocket.FrameType
import io.rsocket.util.PayloadImpl
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom
import org.junit.Test
import reactor.test.StepVerifier

class FrameFragmenterTest {
    @Test
    fun testFragmentWithMetadataAndData() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)

        StepVerifier.create(frameFragmenter.fragment(from)).expectNextCount(16).verifyComplete()
    }

    @Test
    fun testFragmentWithMetadataAndDataWithOddData() {
        val data = createRandomBytes(17)
        val metadata = createRandomBytes(17)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)

        StepVerifier.create(frameFragmenter.fragment(from)).expectNextCount(17).verifyComplete()
    }

    @Test
    fun testFragmentWithMetadataOnly() {
        val data = ByteBuffer.allocate(0)
        val metadata = createRandomBytes(16)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)

        StepVerifier.create(frameFragmenter.fragment(from)).expectNextCount(8).verifyComplete()
    }

    @Test
    fun testFragmentWithDataOnly() {
        val data = createRandomBytes(16)
        val metadata = ByteBuffer.allocate(0)

        val from = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val frameFragmenter = FrameFragmenter(2)

        StepVerifier.create(frameFragmenter.fragment(from)).expectNextCount(8).verifyComplete()
    }

    private fun createRandomBytes(size: Int): ByteBuffer {
        val bytes = ByteArray(size)
        ThreadLocalRandom.current().nextBytes(bytes)
        return ByteBuffer.wrap(bytes)
    }
}
