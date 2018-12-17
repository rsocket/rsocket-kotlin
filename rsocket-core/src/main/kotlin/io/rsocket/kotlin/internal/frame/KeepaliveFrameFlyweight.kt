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
import io.rsocket.kotlin.internal.frame.Utils.LONG_BYTES

internal object KeepaliveFrameFlyweight {
    const val FLAGS_KEEPALIVE_R = 128

    private val LAST_POSITION_OFFSET = FrameHeaderFlyweight.FRAME_HEADER_LENGTH
    private val PAYLOAD_OFFSET = LAST_POSITION_OFFSET + LONG_BYTES

    fun computeFrameLength(dataLength: Int): Int {
        return FrameHeaderFlyweight.computeFrameHeaderLength(
                FrameType.SETUP,
                null,
                dataLength) + LONG_BYTES
    }

    fun encode(byteBuf: ByteBuf,
               flags: Int,
               data: ByteBuf): Int {
        val frameLength = computeFrameLength(data.readableBytes())
        var length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                flags,
                FrameType.KEEPALIVE,
                0)

        // We don't support resumability, last position is always zero
        byteBuf.setLong(length, 0)
        length += LONG_BYTES
        length += FrameHeaderFlyweight.encodeData(byteBuf, length, data)
        return length
    }

    fun payloadOffset(): Int {
        return PAYLOAD_OFFSET
    }
}
