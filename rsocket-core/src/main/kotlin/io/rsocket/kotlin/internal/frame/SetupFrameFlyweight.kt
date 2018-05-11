/*
 * Copyright 2016 Netflix, Inc.
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
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight.FLAGS_M
import java.nio.charset.StandardCharsets

internal object SetupFrameFlyweight {

    const val FLAGS_RESUME_ENABLE = 128
    const val FLAGS_WILL_HONOR_LEASE = 64

    val VALID_FLAGS = FLAGS_RESUME_ENABLE or
            FLAGS_WILL_HONOR_LEASE or
            FLAGS_M

    val CURRENT_VERSION = VersionFlyweight.encode(1, 0)

    // relative to start of passed offset
    private val VERSION_FIELD_OFFSET = FrameHeaderFlyweight.FRAME_HEADER_LENGTH
    private val KEEPALIVE_INTERVAL_FIELD_OFFSET = VERSION_FIELD_OFFSET +
            Utils.INTEGER_BYTES
    private val MAX_LIFETIME_FIELD_OFFSET = KEEPALIVE_INTERVAL_FIELD_OFFSET +
            Utils.INTEGER_BYTES
    private val VARIABLE_DATA_OFFSET = MAX_LIFETIME_FIELD_OFFSET +
            Utils.INTEGER_BYTES

    fun computeFrameLength(
            flags: Int,
            metadataMimeType: String,
            dataMimeType: String,
            metadataLength: Int,
            dataLength: Int): Int =

            computeFrameLength(
                    flags,
                    0,
                    metadataMimeType,
                    dataMimeType,
                    metadataLength,
                    dataLength)

    private fun computeFrameLength(
            flags: Int,
            resumeTokenLength: Int,
            metadataMimeType: String,
            dataMimeType: String,
            metadataLength: Int,
            dataLength: Int): Int {

        var length = FrameHeaderFlyweight.computeFrameHeaderLength(
                FrameType.SETUP,
                metadataLength,
                dataLength)

        length += Utils.INTEGER_BYTES * 3

        if (flags and FLAGS_RESUME_ENABLE != 0) {
            length += Utils.SHORT_BYTES + resumeTokenLength
        }

        length += 1 + metadataMimeType.toByteArray(StandardCharsets.UTF_8).size
        length += 1 + dataMimeType.toByteArray(StandardCharsets.UTF_8).size

        return length
    }

    fun encode(
            byteBuf: ByteBuf,
            flags: Int,
            version: Int,
            keepaliveInterval: Int,
            maxLifetime: Int,
            metadataMimeType: String,
            dataMimeType: String,
            metadata: ByteBuf,
            data: ByteBuf): Int {

        return encode(
                byteBuf,
                flags,
                version,
                keepaliveInterval,
                maxLifetime,
                Unpooled.EMPTY_BUFFER,
                metadataMimeType,
                dataMimeType,
                metadata,
                data)
    }

    // Only exposed for testing, other code shouldn't create
    // frames with resumption tokens for now
    internal fun encode(
            byteBuf: ByteBuf,
            flags: Int,
            version: Int,
            keepaliveInterval: Int,
            maxLifetime: Int,
            resumeToken: ByteBuf,
            metadataMimeType: String,
            dataMimeType: String,
            metadata: ByteBuf,
            data: ByteBuf): Int {
        val frameLength = computeFrameLength(
                flags,
                resumeToken.readableBytes(),
                metadataMimeType,
                dataMimeType,
                metadata.readableBytes(),
                data.readableBytes())

        var length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                flags,
                FrameType.SETUP,
                0)

        byteBuf.setInt(VERSION_FIELD_OFFSET, version)
        byteBuf.setInt(KEEPALIVE_INTERVAL_FIELD_OFFSET, keepaliveInterval)
        byteBuf.setInt(MAX_LIFETIME_FIELD_OFFSET, maxLifetime)

        length += Utils.INTEGER_BYTES * 3

        if (flags and FLAGS_RESUME_ENABLE != 0) {
            byteBuf.setShort(length, resumeToken.readableBytes())
            length += Utils.SHORT_BYTES
            val resumeTokenLength = resumeToken.readableBytes()
            byteBuf.setBytes(length, resumeToken, resumeTokenLength)
            length += resumeTokenLength
        }

        length += putMimeType(byteBuf, length, metadataMimeType)
        length += putMimeType(byteBuf, length, dataMimeType)

        length += FrameHeaderFlyweight.encodeMetadata(
                byteBuf,
                FrameType.SETUP,
                length,
                metadata)

        length += FrameHeaderFlyweight.encodeData(
                byteBuf,
                length,
                data)

        return length
    }

    fun version(byteBuf: ByteBuf): Int = byteBuf.getInt(VERSION_FIELD_OFFSET)

    fun keepaliveInterval(byteBuf: ByteBuf): Int =
            byteBuf.getInt(KEEPALIVE_INTERVAL_FIELD_OFFSET)

    fun maxLifetime(byteBuf: ByteBuf): Int =
            byteBuf.getInt(MAX_LIFETIME_FIELD_OFFSET)

    fun metadataMimeType(byteBuf: ByteBuf): String {
        val bytes = getMimeType(byteBuf, metadataMimetypeOffset(byteBuf))
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun dataMimeType(byteBuf: ByteBuf): String {
        var fieldOffset = metadataMimetypeOffset(byteBuf)

        fieldOffset += 1 + byteBuf.getByte(fieldOffset)

        val bytes = getMimeType(byteBuf, fieldOffset)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun payloadOffset(byteBuf: ByteBuf): Int {
        var fieldOffset = metadataMimetypeOffset(byteBuf)

        val metadataMimeTypeLength = byteBuf.getByte(fieldOffset).toInt()
        fieldOffset += 1 + metadataMimeTypeLength

        val dataMimeTypeLength = byteBuf.getByte(fieldOffset).toInt()
        fieldOffset += 1 + dataMimeTypeLength

        return fieldOffset
    }

    private fun metadataMimetypeOffset(byteBuf: ByteBuf): Int {
        return VARIABLE_DATA_OFFSET + resumeTokenTotalLength(byteBuf)
    }

    private fun resumeTokenTotalLength(byteBuf: ByteBuf): Int =
            if (FrameHeaderFlyweight.flags(byteBuf) and FLAGS_RESUME_ENABLE == 0)
                0
            else
                Utils.SHORT_BYTES + byteBuf.getShort(VARIABLE_DATA_OFFSET)

    private fun putMimeType(
            byteBuf: ByteBuf, fieldOffset: Int, mimeType: String): Int {
        val bytes = mimeType.toByteArray(StandardCharsets.UTF_8)

        byteBuf.setByte(fieldOffset, bytes.size.toByte().toInt())
        byteBuf.setBytes(fieldOffset + 1, bytes)

        return 1 + bytes.size
    }

    private fun getMimeType(byteBuf: ByteBuf, fieldOffset: Int): ByteArray {
        val length = byteBuf.getByte(fieldOffset).toInt()
        val bytes = ByteArray(length)

        byteBuf.getBytes(fieldOffset + 1, bytes)
        return bytes
    }
}
