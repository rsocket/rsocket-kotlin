/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal.fragmentation

import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.DefaultPayload
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

class StreamFramesReassemblerTest {

    @Test
    fun testReassemble() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val from = Frame.Request.from(
                1024, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)
                .retain()
        val frameFragmenter = FrameFragmenter(2)
        val frameReassembler = StreamFramesReassembler(from)
        frameFragmenter.fragment(from)
                .doOnNext { frameReassembler.append(it) }
                .blockingLast()
        val reassemble = frameReassembler.reassemble()
        assertEquals(reassemble.streamId, from.streamId)
        assertEquals(reassemble.type, from.type)

        val reassembleData = reassemble.data
        val reassembleMetadata = reassemble.metadata

        assertTrue(reassembleData.hasRemaining())
        assertTrue(reassembleMetadata.hasRemaining())

        while (reassembleData.hasRemaining()) {
            assertEquals(reassembleData.get(), data.get())
        }

        while (reassembleMetadata.hasRemaining()) {
            assertEquals(reassembleMetadata.get(), metadata.get())
        }
    }

    @Test
    fun testReassembleNullMetadata() {
        val data = createRandomBytes(16)
        val metadata = null

        val from = Frame.Request.from(
                1024, FrameType.REQUEST_RESPONSE, DefaultPayload(data, metadata), 1)
                .retain()
        val frameFragmenter = FrameFragmenter(2)
        val frameReassembler = StreamFramesReassembler(from)
        frameFragmenter.fragment(from)
                .doOnNext { frameReassembler.append(it) }
                .blockingLast()
        val reassemble = frameReassembler.reassemble()
        assertFalse(reassemble.hasMetadata())
        assertFalse(reassemble.metadata.hasRemaining())
    }

    private fun createRandomBytes(size: Int): ByteBuffer {
        val bytes = ByteArray(size)
        ThreadLocalRandom.current().nextBytes(bytes)
        return ByteBuffer.wrap(bytes)
    }
}
