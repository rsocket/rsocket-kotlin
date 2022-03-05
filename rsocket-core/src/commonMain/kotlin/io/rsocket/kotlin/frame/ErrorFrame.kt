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
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.io.*

internal fun ErrorFrame(
    streamId: Int,
    throwable: Throwable,
): ErrorFrame = ErrorFrame(
    streamId,
    (throwable as? RSocketError)?.errorCode ?: when (streamId) {
        0    -> ErrorCode.ConnectionError
        else -> ErrorCode.ApplicationError
    },
    throwable.message?.encodeToByteArray()?.let(::ByteReadPacket) ?: ByteReadPacket.Empty
)

internal class ErrorFrame(
    override val streamId: Int,
    val errorCode: Int,
    val data: ByteReadPacket,
) : Frame() {
    override val type: FrameType get() = FrameType.Error
    override val flags: Int get() = 0

    override fun close() {
        data.close()
    }

    override fun BytePacketBuilder.writeSelf() {
        writeInt(errorCode)
        writePacket(data)
    }

    override fun StringBuilder.appendFlags(): Unit = Unit
    override fun StringBuilder.appendSelf() {
        append("\nError code: ").append(errorCode)
        appendPacket("Data", data)
    }
}

internal fun ByteReadPacket.readError(pool: ObjectPool<ChunkBuffer>, streamId: Int): ErrorFrame {
    val errorCode = readInt()
    val data = readPacket(pool)
    return ErrorFrame(streamId, errorCode, data)
}
