/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.test.*
import kotlinx.io.*
import kotlin.test.*

class KeepAliveFrameTest {
    private val dump = "00000f000000000c80000000000000000064"

    @Test
    fun testEncoding() {
        val frame = KeepAliveFrame(true, 0, packet("d"))
        val bytes = frame.toBufferWithLength().readByteArray()

        assertEquals(dump, bytes.toHexString())
    }

    @Test
    fun testDecoding() {
        val packet = packet(dump.hexToByteArray())
        val frame = packet.toFrameWithLength()

        assertTrue(frame is KeepAliveFrame)
        assertEquals(0, frame.streamId)
        assertTrue(frame.respond)
        assertEquals(0, frame.lastPosition)
        assertEquals("d", frame.data.readString())
    }
}
