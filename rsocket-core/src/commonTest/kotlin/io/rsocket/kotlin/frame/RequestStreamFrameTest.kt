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

class RequestStreamFrameTest {

    @Test
    fun testEncoding() {
        val dump = "000010000000011900000000010000026d6464"
        val frame = RequestStreamFrame(1, 1, payload("d", "md"))
        val bytes = frame.toBufferWithLength().readByteArray()

        assertEquals(dump, bytes.toHexString())
    }

    @Test
    fun testDecoding() {
        val dump = "000010000000011900000000010000026d6464"
        val frame = packet(dump.hexToByteArray()).toFrameWithLength()

        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(1, frame.streamId)
        assertFalse(frame.follows)
        assertFalse(frame.complete)
        assertFalse(frame.next)
        assertEquals(1, frame.initialRequest)
        assertEquals("d", frame.payload.data.readString())
        assertEquals("md", frame.payload.metadata?.readString())
    }

    @Test
    fun testEncodingWithEmptyMetadata() {
        val dump = "00000e0000000119000000000100000064"
        val frame = RequestStreamFrame(1, 1, Payload(packet("d"), Buffer()))
        val bytes = frame.toBufferWithLength().readByteArray()

        assertEquals(dump, bytes.toHexString())
    }

    @Test
    fun testDecodingWithEmptyMetadata() {
        val dump = "00000e0000000119000000000100000064"
        val frame = packet(dump.hexToByteArray()).toFrameWithLength()

        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(1, frame.streamId)
        assertFalse(frame.follows)
        assertFalse(frame.complete)
        assertFalse(frame.next)
        assertEquals(1, frame.initialRequest)
        assertEquals("d", frame.payload.data.readString())
        assertTrue(frame.payload.metadata?.exhausted() ?: false)
    }

    @Test
    fun testEncodingWithNullMetadata() {
        val dump = "00000b0000000118000000000164"
        val frame = RequestStreamFrame(1, 1, payload("d"))
        val bytes = frame.toBufferWithLength().readByteArray()

        assertEquals(dump, bytes.toHexString())
    }

    @Test
    fun testDecodingWithNullMetadata() {
        val dump = "00000b0000000118000000000164"
        val frame = packet(dump.hexToByteArray()).toFrameWithLength()

        assertTrue(frame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(1, frame.streamId)
        assertFalse(frame.follows)
        assertFalse(frame.complete)
        assertFalse(frame.next)
        assertEquals(1, frame.initialRequest)
        assertEquals("d", frame.payload.data.readString())
        assertNull(frame.payload.metadata)
    }

    @Test
    fun testEmptyData() {
        val frame = RequestStreamFrame(3, 10, Payload(EmptyBuffer, packet("md")))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals(10, decodedFrame.initialRequest)
        assertTrue(decodedFrame.payload.data.exhausted())
        assertEquals("md", decodedFrame.payload.metadata?.readString())
    }

    @Test
    fun testEmptyPayload() {
        val frame = RequestStreamFrame(3, 10, Payload(EmptyBuffer, Buffer()))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals(10, decodedFrame.initialRequest)
        assertTrue(decodedFrame.payload.data.exhausted())
        assertTrue(decodedFrame.payload.metadata?.exhausted() ?: false)
    }

    @Test
    fun testMaxRequestN() {
        val frame = RequestStreamFrame(3, Int.MAX_VALUE, payload("d", "md"))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestStream, frame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals(Int.MAX_VALUE, decodedFrame.initialRequest)
        assertEquals("d", decodedFrame.payload.data.readString())
        assertEquals("md", decodedFrame.payload.metadata?.readString())
    }

}
