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
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.frame.io.*

private const val FlagsMask: Int = 1023
private const val FrameTypeShift: Int = 10

abstract class Frame internal constructor(open val type: FrameType) : Closeable {
    abstract val streamId: Int
    abstract val flags: Int

    abstract fun release()

    protected abstract fun BytePacketBuilder.writeSelf()
    protected abstract fun StringBuilder.appendFlags()
    protected abstract fun StringBuilder.appendSelf()

    @DangerousInternalIoApi
    fun toPacket(pool: ObjectPool<ChunkBuffer>): ByteReadPacket {
        check(type.canHaveMetadata || !(flags check Flags.Metadata)) { "bad value for metadata flag" }
        return buildPacket(pool) {
            writeInt(streamId)
            writeShort((type.encodedType shl FrameTypeShift or flags).toShort())
            writeSelf()
        }
    }

    internal fun dump(length: Long): String = buildString {
        append("\n").append(type).append(" frame -> Stream Id: ").append(streamId).append(" Length: ").append(length)
        append("\nFlags: 0b").append(flags.toBinaryString()).append(" (").apply { appendFlags() }.append(")")
        appendSelf()
    }

    protected fun StringBuilder.appendFlag(flag: Char, value: Boolean) {
        append(flag)
        if (value) append(1) else append(0)
    }

    override fun close() {
        release()
    }
}

@DangerousInternalIoApi
fun ByteReadPacket.readFrame(pool: ObjectPool<ChunkBuffer>): Frame = use {
    val streamId = readInt()
    val typeAndFlags = readShort().toInt() and 0xFFFF
    val flags = typeAndFlags and FlagsMask
    when (val type = FrameType(typeAndFlags shr FrameTypeShift)) {
        //stream id = 0
        FrameType.Setup        -> readSetup(pool, flags)
        FrameType.Resume       -> readResume(pool)
        FrameType.ResumeOk     -> readResumeOk()
        FrameType.MetadataPush -> readMetadataPush(pool)
        FrameType.Lease        -> readLease(pool, flags)
        FrameType.KeepAlive    -> readKeepAlive(pool, flags)
        //stream id != 0
        FrameType.Cancel       -> CancelFrame(streamId)
        FrameType.Error        -> readError(streamId)
        FrameType.RequestN     -> readRequestN(streamId)
        FrameType.Extension    -> readExtension(pool, streamId, flags)
        FrameType.Payload,
        FrameType.RequestFnF,
        FrameType.RequestResponse,
                               -> readRequest(pool, type, streamId, flags, withInitial = false)
        FrameType.RequestStream,
        FrameType.RequestChannel,
                               -> readRequest(pool, type, streamId, flags, withInitial = true)
        FrameType.Reserved     -> error("Reserved")
    }
}
