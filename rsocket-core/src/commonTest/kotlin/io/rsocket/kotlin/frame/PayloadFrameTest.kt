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

class PayloadFrameTest {

    @Test
    fun testNextCompleteDataMetadata() {
        val frame = NextCompletePayloadFrame(3, payload("d", "md"))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertTrue(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readString())
        assertEquals("md", decodedFrame.payload.metadata?.readString())
    }

    @Test
    fun testNextCompleteData() {
        val frame = NextCompletePayloadFrame(3, payload("d"))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertTrue(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readString())
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNextCompleteMetadata() {
        val frame = NextCompletePayloadFrame(3, Payload(EmptyBuffer, packet("md")))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertTrue(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertTrue(decodedFrame.payload.data.exhausted())
        assertEquals("md", decodedFrame.payload.metadata?.readString())
    }

    @Test
    fun testNextDataMetadata() {
        val frame = NextPayloadFrame(3, payload("d", "md"))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readString())
        assertEquals("md", decodedFrame.payload.metadata?.readString())
    }

    @Test
    fun testNextData() {
        val frame = NextPayloadFrame(3, payload("d"))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readString())
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNextDataEmptyMetadata() {
        val frame = NextPayloadFrame(3, Payload(packet("d"), Buffer()))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readString())
        assertTrue(decodedFrame.payload.metadata?.exhausted() ?: false)
    }

}
