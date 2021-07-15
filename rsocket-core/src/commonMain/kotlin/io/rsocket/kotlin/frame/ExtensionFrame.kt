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
import io.rsocket.kotlin.payload.*

internal class ExtensionFrame(
    override val streamId: Int,
    val extendedType: Int,
    val payload: Payload,
) : Frame() {
    override val type: FrameType get() = FrameType.Extension
    override val flags: Int get() = if (payload.metadata != null) Flags.Metadata else 0

    override fun release() {
        payload.release()
    }

    override fun BytePacketBuilder.writeSelf() {
        writeInt(extendedType)
        writePayload(payload)
    }

    override fun StringBuilder.appendFlags() {
        appendFlag('M', payload.metadata != null)
    }

    override fun StringBuilder.appendSelf() {
        append("\nExtended type: ").append(extendedType)
        appendPayload(payload)
    }
}

internal fun ByteReadPacket.readExtension(pool: ObjectPool<ChunkBuffer>, streamId: Int, flags: Int): ExtensionFrame {
    val extendedType = readInt()
    val payload = readPayload(pool, flags)
    return ExtensionFrame(streamId, extendedType, payload)
}
