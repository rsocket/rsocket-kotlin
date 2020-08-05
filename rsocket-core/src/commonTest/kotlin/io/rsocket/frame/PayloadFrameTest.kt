/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.frame

import io.ktor.utils.io.core.*
import io.rsocket.payload.*
import kotlin.test.*

class PayloadFrameTest {

    @Test
    fun testNextCompleteDataMetadata() {
        val frame = NextCompletePayloadFrame(3, Payload("d", "md"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertTrue(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertEquals("md", decodedFrame.payload.metadata?.readText())
    }

    @Test
    fun testNextCompleteData() {
        val frame = NextCompletePayloadFrame(3, Payload("d"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertTrue(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNextCompleteMetadata() {
        val frame = NextCompletePayloadFrame(3, Payload(ByteReadPacket.Empty, packet("md")))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertTrue(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertEquals("md", decodedFrame.payload.metadata?.readText())
    }

    @Test
    fun testNextDataMetadata() {
        val frame = NextPayloadFrame(3, Payload("d", "md"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertEquals("md", decodedFrame.payload.metadata?.readText())
    }

    @Test
    fun testNextData() {
        val frame = NextPayloadFrame(3, Payload("d"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNextDataEmptyMetadata() {
        val frame = NextPayloadFrame(3, Payload(packet("d"), ByteReadPacket.Empty))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.Payload, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertTrue(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertEquals(0, decodedFrame.payload.metadata?.remaining)
    }

}
