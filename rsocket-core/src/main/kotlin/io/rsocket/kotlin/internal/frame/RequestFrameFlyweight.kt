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

import io.rsocket.kotlin.internal.frame.Utils.INTEGER_BYTES

import io.netty.buffer.ByteBuf
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType

internal object RequestFrameFlyweight {

    // relative to start of passed offset
    private val INITIAL_REQUEST_N_FIELD_OFFSET =
            FrameHeaderFlyweight.FRAME_HEADER_LENGTH

    fun computeFrameLength(
            type: FrameType,
            metadataLength: Int?,
            dataLength: Int): Int {

        var length = FrameHeaderFlyweight.computeFrameHeaderLength(
                        type,
                        metadataLength,
                        dataLength)

        if (type.hasInitialRequestN()) {
            length += INTEGER_BYTES
        }
        return length
    }

    fun encode(
            byteBuf: ByteBuf,
            streamId: Int,
            flags: Int,
            type: FrameType,
            initialRequestN: Int,
            metadata: ByteBuf?,
            data: ByteBuf): Int {
        if (Frame.isFlagSet(
                        flags,
                        FrameHeaderFlyweight.FLAGS_M) != (metadata != null)) {
            throw IllegalArgumentException("metadata flag set incorrectly")
        }
        val frameLength = computeFrameLength(
                type,
                metadata?.readableBytes(),
                data.readableBytes())

        var length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                flags,
                type,
                streamId)

        byteBuf.setInt(INITIAL_REQUEST_N_FIELD_OFFSET, initialRequestN)
        length += INTEGER_BYTES

        length += FrameHeaderFlyweight.encodeMetadata(
                byteBuf,
                type,
                length,
                metadata)

        length += FrameHeaderFlyweight.encodeData(
                byteBuf,
                length,
                data)

        return length
    }

    fun encode(
            byteBuf: ByteBuf,
            streamId: Int,
            flags: Int,
            type: FrameType,
            metadata: ByteBuf?,
            data: ByteBuf): Int {
        if (Frame.isFlagSet(flags, FrameHeaderFlyweight.FLAGS_M) !=
                (metadata != null)) {
            throw IllegalArgumentException("metadata flag set incorrectly")
        }
        if (type.hasInitialRequestN()) {
            throw AssertionError(
                    "$type must not be encoded without initial request N")
        }
        val frameLength = computeFrameLength(
                type, metadata?.readableBytes(), data.readableBytes())

        var length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                flags,
                type,
                streamId)

        length += FrameHeaderFlyweight.encodeMetadata(
                byteBuf,
                type,
                length,
                metadata)

        length += FrameHeaderFlyweight.encodeData(
                byteBuf,
                length,
                data)

        return length
    }

    fun initialRequestN(byteBuf: ByteBuf): Int {
        return byteBuf.getInt(INITIAL_REQUEST_N_FIELD_OFFSET)
    }

    fun payloadOffset(type: FrameType): Int {
        var result = FrameHeaderFlyweight.FRAME_HEADER_LENGTH

        if (type.hasInitialRequestN()) {
            result += INTEGER_BYTES
        }
        return result
    }
}
