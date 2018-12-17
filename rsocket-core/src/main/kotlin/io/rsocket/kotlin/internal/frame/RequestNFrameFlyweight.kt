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
import io.rsocket.kotlin.internal.frame.Utils.INTEGER_BYTES

internal object RequestNFrameFlyweight {

    // relative to start of passed offset
    private val REQUEST_N_FIELD_OFFSET = FrameHeaderFlyweight.FRAME_HEADER_LENGTH

    fun computeFrameLength(): Int {
        val length = FrameHeaderFlyweight.computeFrameHeaderLength(
                FrameType.REQUEST_N,
                0,
                0)

        return length + INTEGER_BYTES
    }

    fun encode(byteBuf: ByteBuf,
               streamId: Int,
               requestN: Int): Int {
        val frameLength = computeFrameLength()

        val length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                0,
                FrameType.REQUEST_N,
                streamId)

        byteBuf.setInt(REQUEST_N_FIELD_OFFSET, requestN)

        return length + INTEGER_BYTES
    }

    fun requestN(byteBuf: ByteBuf): Int {
        return byteBuf.getInt(REQUEST_N_FIELD_OFFSET)
    }

    fun payloadOffset(): Int {
        return FrameHeaderFlyweight.FRAME_HEADER_LENGTH + INTEGER_BYTES
    }
}
