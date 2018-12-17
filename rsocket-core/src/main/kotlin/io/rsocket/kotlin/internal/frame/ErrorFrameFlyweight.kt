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
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.exceptions.RSocketException
import io.rsocket.kotlin.internal.frame.Utils.INTEGER_BYTES
import java.nio.charset.StandardCharsets

internal object ErrorFrameFlyweight {

    // defined error codes
    const val INVALID_SETUP = 0x00000001
    const val UNSUPPORTED_SETUP = 0x00000002
    const val REJECTED_SETUP = 0x00000003
    const val REJECTED_RESUME = 0x00000004
    const val CONNECTION_ERROR = 0x00000101
    const val CONNECTION_CLOSE = 0x00000102
    const val APPLICATION_ERROR = 0x00000201
    const val REJECTED = 0x00000202
    const val CANCELED = 0x00000203
    const val INVALID = 0x00000204

    // relative to start of passed offset
    private val ERROR_CODE_FIELD_OFFSET = FrameHeaderFlyweight.FRAME_HEADER_LENGTH
    private val PAYLOAD_OFFSET = ERROR_CODE_FIELD_OFFSET + INTEGER_BYTES

    fun computeFrameLength(dataLength: Int): Int {
        val length = FrameHeaderFlyweight.computeFrameHeaderLength(
                FrameType.ERROR,
                null,
                dataLength)
        return length + INTEGER_BYTES
    }

    fun encode(
            byteBuf: ByteBuf,
            streamId: Int,
            errorCode: Int,
            data: ByteBuf): Int {
        val frameLength = computeFrameLength(data.readableBytes())

        var length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                0,
                FrameType.ERROR,
                streamId)

        byteBuf.setInt(ERROR_CODE_FIELD_OFFSET, errorCode)
        length += INTEGER_BYTES

        length += FrameHeaderFlyweight.encodeData(byteBuf, length, data)

        return length
    }

    fun errorCodeFromException(ex: Throwable): Int {
        return (ex as? RSocketException)?.errorCode() ?: APPLICATION_ERROR

    }

    fun errorCode(byteBuf: ByteBuf): Int {
        return byteBuf.getInt(ERROR_CODE_FIELD_OFFSET)
    }

    fun payloadOffset(): Int {
        return FrameHeaderFlyweight.FRAME_HEADER_LENGTH + INTEGER_BYTES
    }

    fun message(content: ByteBuf): String {
        return FrameHeaderFlyweight.sliceFrameData(content)
                .toString(StandardCharsets.UTF_8)
    }
}
