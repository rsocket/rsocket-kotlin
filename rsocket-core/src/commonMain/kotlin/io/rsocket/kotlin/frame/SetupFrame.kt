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

private const val HonorLeaseFlag = 64
private const val ResumeEnabledFlag = 128

internal class SetupFrame(
    val version: Version, //TODO check
    val honorLease: Boolean,
    val keepAliveIntervalMillis: Int,
    val keepAliveMaxLifetimeMillis: Int,
    val resumeToken: ByteReadPacket?,
    val metadataMimeTypeText: String,
    val dataMimeTypeText: String,
    val payload: Payload,
) : Frame() {
    override val type: FrameType get() = FrameType.Setup
    override val streamId: Int get() = 0
    override val flags: Int
        get() {
            var flags = 0
            if (honorLease) flags = flags or HonorLeaseFlag
            if (resumeToken != null) flags = flags or ResumeEnabledFlag
            if (payload.metadata != null) flags = flags or Flags.Metadata
            return flags
        }

    override fun close() {
        resumeToken?.close()
        payload.close()
    }

    override fun BytePacketBuilder.writeSelf() {
        writeVersion(version)
        writeInt(keepAliveIntervalMillis)
        writeInt(keepAliveMaxLifetimeMillis)
        writeResumeToken(resumeToken)
        writeStringMimeType(metadataMimeTypeText)
        writeStringMimeType(dataMimeTypeText)
        writePayload(payload)
    }

    override fun StringBuilder.appendFlags() {
        appendFlag('M', payload.metadata != null)
        appendFlag('R', resumeToken != null)
        appendFlag('L', honorLease)
    }

    override fun StringBuilder.appendSelf() {
        append("\nVersion: ").append(version.toString()).append(" Honor lease: ").append(honorLease).append("\n")
        append("Keep alive: interval=").append(keepAliveIntervalMillis).append(" ms,")
        append("max lifetime=").append(keepAliveMaxLifetimeMillis).append(" ms\n")
        append("Metadata mime type: ").append(metadataMimeTypeText).append("\n")
        append("Data mime type: ").append(dataMimeTypeText)
        appendPayload(payload)
    }
}

internal fun ByteReadPacket.readSetup(pool: ObjectPool<ChunkBuffer>, flags: Int): SetupFrame {
    val version = readVersion()
    val keepAliveIntervalMillis = readInt()
    val keepAliveMaxLifetimeMillis = readInt()
    val resumeToken = if (flags check ResumeEnabledFlag) readResumeToken(pool) else null
    val metadataMimeTypeText = readStringMimeType()
    val dataMimeTypeText = readStringMimeType()
    val payload = readPayload(pool, flags)
    return SetupFrame(
        version = version,
        honorLease = flags check HonorLeaseFlag,
        keepAliveIntervalMillis = keepAliveIntervalMillis,
        keepAliveMaxLifetimeMillis = keepAliveMaxLifetimeMillis,
        resumeToken = resumeToken,
        metadataMimeTypeText = metadataMimeTypeText,
        dataMimeTypeText = dataMimeTypeText,
        payload = payload
    )
}

private fun ByteReadPacket.readStringMimeType(): String {
    val length = readByte().toInt()
    return readTextExactBytes(length)
}

private fun BytePacketBuilder.writeStringMimeType(mimeType: String) {
    val bytes = mimeType.encodeToByteArray() //TODO check
    writeByte(bytes.size.toByte())
    writeFully(bytes)
}
