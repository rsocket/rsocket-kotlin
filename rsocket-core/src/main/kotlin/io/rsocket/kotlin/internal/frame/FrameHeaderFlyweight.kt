/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.kotlin.internal.frame

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.internal.frame.Utils.INTEGER_BYTES
import io.rsocket.kotlin.internal.frame.Utils.SHORT_BYTES

/**
 * Per connection frame flyweight.
 *
 *
 * Not the latest frame layout, but close. Does not
 * include - fragmentation / reassembly - encode should remove Type param and
 * have it as part of method name (1 encode per type?)
 *
 * Not thread-safe. Assumed to be used single-threaded
 */
internal object FrameHeaderFlyweight {

    val FRAME_HEADER_LENGTH: Int

    private const val FRAME_TYPE_BITS = 6
    private const val FRAME_TYPE_SHIFT = 16 - FRAME_TYPE_BITS
    private const val FRAME_FLAGS_MASK = 1023

    val FRAME_LENGTH_SIZE = 3
    val FRAME_LENGTH_MASK = 0xFFFFFF

    private const val FRAME_LENGTH_FIELD_OFFSET: Int = 0
    private val FRAME_TYPE_AND_FLAGS_FIELD_OFFSET: Int
    private val STREAM_ID_FIELD_OFFSET: Int
    private val PAYLOAD_OFFSET: Int

    val FLAGS_I = 512
    const val FLAGS_M = 256

    const val FLAGS_F = 128
    const val FLAGS_C = 64
    const val FLAGS_N = 32

    init {
        STREAM_ID_FIELD_OFFSET = FRAME_LENGTH_FIELD_OFFSET + FRAME_LENGTH_SIZE
        FRAME_TYPE_AND_FLAGS_FIELD_OFFSET = STREAM_ID_FIELD_OFFSET + INTEGER_BYTES
        PAYLOAD_OFFSET = FRAME_TYPE_AND_FLAGS_FIELD_OFFSET + SHORT_BYTES
        FRAME_HEADER_LENGTH = PAYLOAD_OFFSET
    }

    fun computeFrameHeaderLength(
            frameType: FrameType,
            metadataLength: Int?,
            dataLength: Int): Int {
        return PAYLOAD_OFFSET +
                computeMetadataLength(frameType, metadataLength) +
                dataLength
    }

    fun encodeFrameHeader(
            byteBuf: ByteBuf,
            frameLength: Int,
            flags: Int,
            frameType: FrameType,
            streamId: Int): Int {
        if (frameLength and FRAME_LENGTH_MASK.inv() != 0) {
            throw IllegalArgumentException(
                    "Frame length is larger than 24 bits")
        }

        // frame length field needs to be excluded from the length
        encodeLength(byteBuf,
                FRAME_LENGTH_FIELD_OFFSET,
                frameLength - FRAME_LENGTH_SIZE)

        byteBuf.setInt(STREAM_ID_FIELD_OFFSET, streamId)
        val typeAndFlags = (frameType.encodedType shl FRAME_TYPE_SHIFT or flags)
        byteBuf.setShort(FRAME_TYPE_AND_FLAGS_FIELD_OFFSET, typeAndFlags)

        return FRAME_HEADER_LENGTH
    }

    fun encodeMetadata(
            byteBuf: ByteBuf,
            frameType: FrameType,
            metadataOffset: Int,
            metadata: ByteBuf?): Int {
        var length = 0

        if (metadata != null) {
            val metadataLength = metadata.readableBytes()

            var typeAndFlags = byteBuf
                    .getShort(FRAME_TYPE_AND_FLAGS_FIELD_OFFSET).toInt()
            typeAndFlags = typeAndFlags or FLAGS_M
            byteBuf.setShort(
                    FRAME_TYPE_AND_FLAGS_FIELD_OFFSET,
                    typeAndFlags.toShort().toInt())

            if (hasMetadataLengthField(frameType)) {
                encodeLength(byteBuf, metadataOffset, metadataLength)
                length += FRAME_LENGTH_SIZE
            }
            byteBuf.setBytes(metadataOffset + length, metadata)
            length += metadataLength
        }

        return length
    }

    fun encodeData(byteBuf: ByteBuf,
                   dataOffset: Int,
                   data: ByteBuf): Int {
        var length = 0
        val dataLength = data.readableBytes()

        if (0 < dataLength) {
            byteBuf.setBytes(dataOffset, data)
            length += dataLength
        }
        return length
    }

    // only used for types simple enough that they don't have their
    // own FrameFlyweights
    fun encode(
            byteBuf: ByteBuf,
            streamId: Int,
            flags: Int,
            frameType: FrameType,
            metadata: ByteBuf?,
            data: ByteBuf): Int {
        var f = flags
        if (Frame.isFlagSet(f, FLAGS_M) != (metadata != null)) {
            throw IllegalStateException("bad value for metadata flag")
        }

        val frameLength = computeFrameHeaderLength(
                frameType, metadata?.readableBytes(), data.readableBytes())

        val outFrameType: FrameType
        when (frameType) {
            FrameType.PAYLOAD -> throw IllegalArgumentException(
                    "Don't encode raw PAYLOAD frames, " +
                            "use NEXT_COMPLETE, COMPLETE or NEXT")
            FrameType.NEXT_COMPLETE -> {
                outFrameType = FrameType.PAYLOAD
                f = f or (FLAGS_C or FLAGS_N)
            }
            FrameType.COMPLETE -> {
                outFrameType = FrameType.PAYLOAD
                f = f or FLAGS_C
            }
            FrameType.NEXT -> {
                outFrameType = FrameType.PAYLOAD
                f = f or FLAGS_N
            }
            else -> outFrameType = frameType
        }
        var length = encodeFrameHeader(
                byteBuf,
                frameLength,
                f,
                outFrameType,
                streamId)

        length += encodeMetadata(byteBuf, frameType, length, metadata)
        length += encodeData(byteBuf, length, data)

        return length
    }

    fun flags(byteBuf: ByteBuf): Int {
        val typeAndFlags = byteBuf.getShort(FRAME_TYPE_AND_FLAGS_FIELD_OFFSET)
        return typeAndFlags.toInt() and FRAME_FLAGS_MASK
    }

    fun frameType(byteBuf: ByteBuf): FrameType {
        val typeAndFlags = byteBuf
                .getShort(FRAME_TYPE_AND_FLAGS_FIELD_OFFSET).toInt()
        var result = FrameType.from(typeAndFlags shr FRAME_TYPE_SHIFT)!!

        if (FrameType.PAYLOAD === result) {
            val flags = typeAndFlags and FRAME_FLAGS_MASK

            val complete = FLAGS_C == flags and FLAGS_C
            val next = FLAGS_N == flags and FLAGS_N
            result = if (next && complete) {
                FrameType.NEXT_COMPLETE
            } else if (complete) {
                FrameType.COMPLETE
            } else if (next) {
                FrameType.NEXT
            } else {
                throw IllegalArgumentException(
                        "Payload must set either or both of NEXT and COMPLETE.")
            }
        }
        return result
    }

    fun streamId(byteBuf: ByteBuf): Int {
        return byteBuf.getInt(STREAM_ID_FIELD_OFFSET)
    }

    fun sliceFrameData(byteBuf: ByteBuf): ByteBuf {
        val frameType = frameType(byteBuf)
        val frameLength = frameLength(byteBuf)
        val dataLength = dataLength(byteBuf, frameType)
        val dataOffset = dataOffset(byteBuf, frameType, frameLength)
        var result = Unpooled.EMPTY_BUFFER

        if (0 < dataLength) {
            result = byteBuf.slice(dataOffset, dataLength)
        }
        return result
    }

    fun sliceFrameMetadata(byteBuf: ByteBuf): ByteBuf? {
        val frameType = frameType(byteBuf)
        val frameLength = frameLength(byteBuf)
        val metadataLength = metadataLength(byteBuf, frameType, frameLength)
                ?: return null

        var metadataOffset = metadataOffset(byteBuf)
        if (hasMetadataLengthField(frameType)) {
            metadataOffset += FRAME_LENGTH_SIZE
        }
        var result = Unpooled.EMPTY_BUFFER

        if (0 < metadataLength) {
            result = byteBuf.slice(metadataOffset, metadataLength)
        }
        return result
    }

    fun frameLength(byteBuf: ByteBuf): Int {
        // frame length field was excluded from the length so we
        // will add it to represent the entire block
        return decodeLength(
                byteBuf,
                FRAME_LENGTH_FIELD_OFFSET) + FRAME_LENGTH_SIZE
    }

    private fun metadataFieldLength(byteBuf: ByteBuf,
                                    frameType: FrameType,
                                    frameLength: Int): Int {
        return computeMetadataLength(
                frameType,
                metadataLength(byteBuf, frameType, frameLength))
    }

    fun metadataLength(
            byteBuf: ByteBuf,
            frameType: FrameType,
            frameLength: Int): Int? {
        return if (!hasMetadataLengthField(frameType)) {
            frameLength - metadataOffset(byteBuf)
        } else {
            decodeMetadataLength(byteBuf, metadataOffset(byteBuf))
        }
    }

    internal fun decodeMetadataLength(byteBuf: ByteBuf,
                                      metadataOffset: Int): Int? {
        val flags = flags(byteBuf)
        return if (FLAGS_M == FLAGS_M and flags) {
            decodeLength(byteBuf, metadataOffset)
        } else {
            null
        }
    }

    private fun computeMetadataLength(frameType: FrameType,
                                      length: Int?): Int {
        return if (!hasMetadataLengthField(frameType)) {
            // Frames with only metadata does not need metadata length field
            length ?: 0
        } else {
            if (length == null) 0 else length + FRAME_LENGTH_SIZE
        }
    }

    fun hasMetadataLengthField(frameType: FrameType): Boolean {
        return frameType.canHaveData()
    }

    fun encodeLength(byteBuf: ByteBuf, offset: Int, length: Int) {
        if (length and FRAME_LENGTH_MASK.inv() != 0) {
            throw IllegalArgumentException("Length is larger than 24 bits")
        }
        // Write each byte separately in reverse order, this mean we
        // can write 1 << 23 without overflowing.
        byteBuf.setByte(offset, length shr 16)
        byteBuf.setByte(offset + 1, length shr 8)
        byteBuf.setByte(offset + 2, length)
    }

    private fun decodeLength(byteBuf: ByteBuf, offset: Int): Int {
        var length = byteBuf.getByte(offset).toInt() and 0xFF shl 16
        length = length or (byteBuf.getByte(offset + 1).toInt() and 0xFF shl 8)
        length = length or (byteBuf.getByte(offset + 2).toInt() and 0xFF)
        return length
    }

    fun dataLength(byteBuf: ByteBuf, frameType: FrameType): Int {
        return dataLength(byteBuf, frameType, payloadOffset(byteBuf))
    }

    internal fun dataLength(byteBuf: ByteBuf,
                            frameType: FrameType,
                            payloadOffset: Int): Int {
        val frameLength = frameLength(byteBuf)
        val metadataLength = metadataFieldLength(
                byteBuf,
                frameType,
                frameLength)
        return frameLength - metadataLength - payloadOffset
    }

    fun payloadLength(byteBuf: ByteBuf): Int {
        val frameLength = frameLength(byteBuf)
        val payloadOffset = payloadOffset(byteBuf)
        return frameLength - payloadOffset
    }

    private fun payloadOffset(byteBuf: ByteBuf): Int {
        val typeAndFlags = byteBuf
                .getShort(FRAME_TYPE_AND_FLAGS_FIELD_OFFSET).toInt()
        val frameType = FrameType.from(typeAndFlags shr FRAME_TYPE_SHIFT)

        return when (frameType) {
            FrameType.SETUP -> SetupFrameFlyweight.payloadOffset(byteBuf)
            FrameType.ERROR -> ErrorFrameFlyweight.payloadOffset()
            FrameType.LEASE -> LeaseFrameFlyweight.payloadOffset()
            FrameType.KEEPALIVE -> KeepaliveFrameFlyweight.payloadOffset()
            FrameType.REQUEST_RESPONSE,
            FrameType.FIRE_AND_FORGET,
            FrameType.REQUEST_STREAM,
            FrameType.REQUEST_CHANNEL -> RequestFrameFlyweight.payloadOffset(frameType)
            FrameType.REQUEST_N -> RequestNFrameFlyweight.payloadOffset()
            else -> PAYLOAD_OFFSET
        }
    }

    fun metadataOffset(byteBuf: ByteBuf): Int = payloadOffset(byteBuf)

    fun dataOffset(byteBuf: ByteBuf,
                   frameType: FrameType,
                   frameLength: Int): Int {
        return payloadOffset(byteBuf) +
                metadataFieldLength(byteBuf, frameType, frameLength)
    }
}
