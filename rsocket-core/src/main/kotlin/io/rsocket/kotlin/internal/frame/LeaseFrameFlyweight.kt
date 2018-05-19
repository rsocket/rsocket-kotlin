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
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.internal.frame.Utils.INTEGER_BYTES

internal object LeaseFrameFlyweight {

    // relative to start of passed offset
    private val TTL_FIELD_OFFSET = FrameHeaderFlyweight.FRAME_HEADER_LENGTH
    private val NUM_REQUESTS_FIELD_OFFSET = TTL_FIELD_OFFSET + INTEGER_BYTES
    private val PAYLOAD_OFFSET = NUM_REQUESTS_FIELD_OFFSET + INTEGER_BYTES

    fun computeFrameLength(metadataLength: Int): Int {
        val length = FrameHeaderFlyweight.computeFrameHeaderLength(
                FrameType.LEASE, metadataLength,
                0)
        return length + INTEGER_BYTES * 2
    }

    fun encode(
            byteBuf: ByteBuf,
            ttl: Int,
            numRequests: Int,
            metadata: ByteBuf): Int {
        val frameLength = computeFrameLength(metadata.readableBytes())
        var length = FrameHeaderFlyweight.encodeFrameHeader(
                byteBuf,
                frameLength,
                0,
                FrameType.LEASE,
                0)

        byteBuf.setInt(TTL_FIELD_OFFSET, ttl)
        byteBuf.setInt(NUM_REQUESTS_FIELD_OFFSET, numRequests)

        length += INTEGER_BYTES * 2
        length += FrameHeaderFlyweight.encodeMetadata(
                byteBuf,
                FrameType.LEASE,
                length,
                metadata)

        return length
    }

    fun ttl(byteBuf: ByteBuf): Int {
        return byteBuf.getInt(TTL_FIELD_OFFSET)
    }

    fun numRequests(byteBuf: ByteBuf): Int {
        return byteBuf.getInt(NUM_REQUESTS_FIELD_OFFSET)
    }

    fun payloadOffset(): Int {
        return PAYLOAD_OFFSET
    }
}
