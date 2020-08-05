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

import io.ktor.util.*
import io.ktor.utils.io.core.*
import io.rsocket.payload.*
import kotlin.test.*

class RequestStreamFrameTest {

    @Test
    fun testEncoding() {
        val dump = "000010000000011900000000010000026d6464"
        val frame = RequestStreamFrame(1, 1, Payload("d", "md"))
        val bytes = frame.toPacketWithLength().readBytes()

        assertEquals(dump, hex(bytes))
    }

    @Test
    fun testDecoding() {
        val dump = "000010000000011900000000010000026d6464"
        val frame = ByteReadPacket(hex(dump)).toFrameWithLength()

        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(1, frame.streamId)
        assertFalse(frame.follows)
        assertFalse(frame.complete)
        assertFalse(frame.next)
        assertEquals(1, frame.initialRequest)
        assertEquals("d", frame.payload.data.readText())
        assertEquals("md", frame.payload.metadata?.readText())
    }

    @Test
    fun testEncodingWithEmptyMetadata() {
        val dump = "00000e0000000119000000000100000064"
        val frame = RequestStreamFrame(1, 1, Payload(packet("d"), ByteReadPacket.Empty))
        val bytes = frame.toPacketWithLength().readBytes()

        assertEquals(dump, hex(bytes))
    }

    @Test
    fun testDecodingWithEmptyMetadata() {
        val dump = "00000e0000000119000000000100000064"
        val frame = ByteReadPacket(hex(dump)).toFrameWithLength()

        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(1, frame.streamId)
        assertFalse(frame.follows)
        assertFalse(frame.complete)
        assertFalse(frame.next)
        assertEquals(1, frame.initialRequest)
        assertEquals("d", frame.payload.data.readText())
        assertEquals(0, frame.payload.metadata?.remaining)
    }

    @Test
    fun testEncodingWithNullMetadata() {
        val dump = "00000b0000000118000000000164"
        val frame = RequestStreamFrame(1, 1, Payload("d"))
        val bytes = frame.toPacketWithLength().readBytes()

        assertEquals(dump, hex(bytes))
    }

    @Test
    fun testDecodingWithNullMetadata() {
        val dump = "00000b0000000118000000000164"
        val frame = ByteReadPacket(hex(dump)).toFrameWithLength()

        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(1, frame.streamId)
        assertFalse(frame.follows)
        assertFalse(frame.complete)
        assertFalse(frame.next)
        assertEquals(1, frame.initialRequest)
        assertEquals("d", frame.payload.data.readText())
        assertNull(frame.payload.metadata)
    }

    @Test
    fun testEmptyData() {
        val frame = RequestStreamFrame(3, 10, Payload(ByteReadPacket.Empty, packet("md")))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals(10, decodedFrame.initialRequest)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertEquals("md", decodedFrame.payload.metadata?.readText())
    }

    @Test
    fun testEmptyPayload() {
        val frame = RequestStreamFrame(3, 10, Payload(ByteReadPacket.Empty, ByteReadPacket.Empty))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals(10, decodedFrame.initialRequest)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertEquals(0, decodedFrame.payload.metadata?.remaining)
    }

    @Test
    fun testMaxRequestN() {
        val frame = RequestStreamFrame(3, Int.MAX_VALUE, Payload("d", "md"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals(Int.MAX_VALUE, decodedFrame.initialRequest)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertEquals("md", decodedFrame.payload.metadata?.readText())
    }

}
