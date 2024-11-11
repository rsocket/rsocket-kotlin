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

import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.io.*
import kotlin.test.*

class ExtensionFrameTest {

    private val streamId = 1
    private val extendedType = 1
    private val data = "DATA"
    private val metadata = "METADATA"

    @Test
    fun testData() {
        val frame = ExtensionFrame(streamId, extendedType, payload(data))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is ExtensionFrame)
        assertEquals(streamId, decodedFrame.streamId)
        assertEquals(extendedType, decodedFrame.extendedType)
        assertNull(decodedFrame.payload.metadata)
        assertEquals(data, decodedFrame.payload.data.readString())
    }

    @Test
    fun testMetadata() {
        val frame = ExtensionFrame(1, extendedType, Payload(EmptyBuffer, packet(metadata)))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is ExtensionFrame)
        assertEquals(streamId, decodedFrame.streamId)
        assertEquals(extendedType, decodedFrame.extendedType)
        assertEquals(metadata, decodedFrame.payload.metadata?.readString())
        assertTrue(decodedFrame.payload.data.exhausted())
    }

    @Test
    fun testDataMetadata() {
        val frame = ExtensionFrame(streamId, extendedType, payload(data, metadata))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is ExtensionFrame)
        assertEquals(streamId, decodedFrame.streamId)
        assertEquals(extendedType, decodedFrame.extendedType)
        assertEquals(metadata, decodedFrame.payload.metadata?.readString())
        assertEquals(data, decodedFrame.payload.data.readString())
    }
}
