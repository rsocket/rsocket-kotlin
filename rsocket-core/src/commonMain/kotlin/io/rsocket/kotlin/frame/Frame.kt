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

package io.rsocket.kotlin.frame

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.io.*

private const val FlagsMask: Int = 1023
private const val FrameTypeShift: Int = 10

abstract class Frame(open val type: FrameType) {
    abstract val streamId: Int
    abstract val flags: Int

    protected abstract fun BytePacketBuilder.writeSelf()

    fun toPacket(): ByteReadPacket {
        check(type.canHaveMetadata || !(flags check Flags.Metadata)) { "bad value for metadata flag" }
        return buildPacket {
            writeInt(streamId)
            writeShort((type.encodedType shl FrameTypeShift or flags).toShort())
            writeSelf()
        }
    }
}

fun ByteReadPacket.toFrame(): Frame = use {
    val streamId = readInt()
    val typeAndFlags = readShort().toInt() and 0xFFFF
    val flags = typeAndFlags and FlagsMask
    when (val type = FrameType(typeAndFlags shr FrameTypeShift)) {
        //stream id = 0
        FrameType.Setup           -> readSetup(flags)
        FrameType.Resume          -> readResume()
        FrameType.ResumeOk        -> readResumeOk()
        FrameType.MetadataPush    -> readMetadataPush()
        FrameType.Lease           -> readLease(flags)
        FrameType.KeepAlive       -> readKeepAlive(flags)
        //stream id != 0
        FrameType.Cancel          -> CancelFrame(streamId)
        FrameType.Error           -> readError(streamId)
        FrameType.RequestN        -> readRequestN(streamId)
        FrameType.Extension       -> readExtension(streamId, flags)
        FrameType.Payload,
        FrameType.RequestFnF,
        FrameType.RequestResponse -> readRequest(type, streamId, flags, withInitial = false)
        FrameType.RequestStream,
        FrameType.RequestChannel  -> readRequest(type, streamId, flags, withInitial = true)
        FrameType.Reserved        -> error("Reserved")
    }
}
