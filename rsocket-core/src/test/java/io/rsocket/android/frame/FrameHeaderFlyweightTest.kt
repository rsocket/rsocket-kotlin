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

package io.rsocket.android.frame

import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_M
import io.rsocket.android.frame.FrameHeaderFlyweight.FRAME_HEADER_LENGTH
import org.junit.Assert.*

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.rsocket.FrameType
import org.junit.Test

class FrameHeaderFlyweightTest {

    private val byteBuf = Unpooled.buffer(1024)

    @Test
    fun headerSize() {
        val frameLength = 123456
        FrameHeaderFlyweight.encodeFrameHeader(byteBuf, frameLength, 0, FrameType.SETUP, 0)
        assertEquals(frameLength.toLong(), FrameHeaderFlyweight.frameLength(byteBuf).toLong())
    }

    @Test
    fun headerSizeMax() {
        val frameLength = FRAME_MAX_SIZE
        FrameHeaderFlyweight.encodeFrameHeader(byteBuf, frameLength, 0, FrameType.SETUP, 0)
        assertEquals(frameLength.toLong(), FrameHeaderFlyweight.frameLength(byteBuf).toLong())
    }

    @Test(expected = IllegalArgumentException::class)
    fun headerSizeTooLarge() {
        FrameHeaderFlyweight.encodeFrameHeader(byteBuf, FRAME_MAX_SIZE + 1, 0, FrameType.SETUP, 0)
    }

    @Test
    fun frameLength() {
        val length = FrameHeaderFlyweight.encode(
                byteBuf, 0, FLAGS_M, FrameType.SETUP, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER)
        assertEquals(length.toLong(), 12) // 72 bits
    }

    @Test
    fun frameLengthNullMetadata() {
        val length = FrameHeaderFlyweight.encode(byteBuf, 0, 0, FrameType.SETUP, null, Unpooled.EMPTY_BUFFER)
        assertEquals(length.toLong(), 9) // 72 bits
    }

    @Test
    fun metadataLength() {
        val metadata = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4))
        FrameHeaderFlyweight.encode(
                byteBuf, 0, FLAGS_M, FrameType.SETUP, metadata, Unpooled.EMPTY_BUFFER)
        assertEquals(
                4,
                FrameHeaderFlyweight.decodeMetadataLength(byteBuf, FrameHeaderFlyweight.FRAME_HEADER_LENGTH)!!
                        .toLong())
    }

    @Test
    fun dataLength() {
        val data = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4, 5))
        val length = FrameHeaderFlyweight.encode(
                byteBuf, 0, FLAGS_M, FrameType.SETUP, Unpooled.EMPTY_BUFFER, data)
        assertEquals(
                5,
                FrameHeaderFlyweight.dataLength(
                        byteBuf, FrameType.SETUP, FrameHeaderFlyweight.FRAME_HEADER_LENGTH).toLong())
    }

    @Test
    fun metadataSlice() {
        val metadata = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4))
        FrameHeaderFlyweight.encode(
                byteBuf, 0, FLAGS_M, FrameType.REQUEST_RESPONSE, metadata, Unpooled.EMPTY_BUFFER)
        metadata.resetReaderIndex()

        assertEquals(metadata, FrameHeaderFlyweight.sliceFrameMetadata(byteBuf))
    }

    @Test
    fun dataSlice() {
        val data = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4, 5))
        FrameHeaderFlyweight.encode(
                byteBuf, 0, FLAGS_M, FrameType.REQUEST_RESPONSE, Unpooled.EMPTY_BUFFER, data)
        data.resetReaderIndex()

        assertEquals(data, FrameHeaderFlyweight.sliceFrameData(byteBuf))
    }

    @Test
    fun streamId() {
        val streamId = 1234
        FrameHeaderFlyweight.encode(
                byteBuf, streamId, FLAGS_M, FrameType.SETUP, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER)
        assertEquals(streamId.toLong(), FrameHeaderFlyweight.streamId(byteBuf).toLong())
    }

    @Test
    fun typeAndFlag() {
        val frameType = FrameType.FIRE_AND_FORGET
        val flags = 951
        FrameHeaderFlyweight.encode(
                byteBuf, 0, flags, frameType, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER)

        assertEquals(flags.toLong(), FrameHeaderFlyweight.flags(byteBuf).toLong())
        assertEquals(frameType, FrameHeaderFlyweight.frameType(byteBuf))
    }

    @Test
    fun typeAndFlagTruncated() {
        val frameType = FrameType.SETUP
        val flags = 1975 // 1 bit too many
        FrameHeaderFlyweight.encode(
                byteBuf, 0, flags, FrameType.SETUP, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER)

        assertNotEquals(flags.toLong(), FrameHeaderFlyweight.flags(byteBuf).toLong())
        assertEquals((flags and 1023).toLong(), FrameHeaderFlyweight.flags(byteBuf).toLong())
        assertEquals(frameType, FrameHeaderFlyweight.frameType(byteBuf))
    }

    @Test
    fun missingMetadataLength() {
        for (frameType in FrameType.values()) {
            when (frameType) {
                FrameType.UNDEFINED -> {
                }
                FrameType.CANCEL, FrameType.METADATA_PUSH, FrameType.LEASE -> assertFalse(
                        "!hasMetadataLengthField(): " + frameType,
                        FrameHeaderFlyweight.hasMetadataLengthField(frameType))
                else -> if (frameType.canHaveMetadata()) {
                    assertTrue(
                            "hasMetadataLengthField(): " + frameType,
                            FrameHeaderFlyweight.hasMetadataLengthField(frameType))
                }
            }
        }
    }

    @Test
    fun wireFormat() {
        val expectedBuffer = Unpooled.buffer(1024)
        var currentIndex = 0
        // frame length
        val frameLength = FrameHeaderFlyweight.FRAME_HEADER_LENGTH - FrameHeaderFlyweight.FRAME_LENGTH_SIZE
        expectedBuffer.setInt(currentIndex, frameLength shl 8)
        currentIndex += 3
        // stream id
        expectedBuffer.setInt(currentIndex, 5)
        currentIndex += Integer.BYTES
        // flags and frame type
        expectedBuffer.setShort(currentIndex, 10336.toShort().toInt())
        currentIndex += java.lang.Short.BYTES

        val frameType = FrameType.NEXT_COMPLETE
        FrameHeaderFlyweight.encode(byteBuf, 5, 0, frameType, null, Unpooled.EMPTY_BUFFER)

        val expected = expectedBuffer.slice(0, currentIndex)
        val actual = byteBuf.slice(0, FRAME_HEADER_LENGTH)

        assertEquals(ByteBufUtil.hexDump(expected), ByteBufUtil.hexDump(actual))
    }

    companion object {
        // Taken from spec
        private val FRAME_MAX_SIZE = 16777215
    }
}
