/*
 * Copyright 2015-2022 the original author or authors.
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
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*

private const val HonorLeaseFlag = 64
private const val ResumeEnabledFlag = 128

internal class SetupFrame(
    val version: Version, //TODO check
    val honorLease: Boolean,
    val keepAlive: KeepAlive,
    val resumeToken: ByteReadPacket?,
    val payloadMimeType: PayloadMimeType,
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
        writeInt(keepAlive.intervalMillis)
        writeInt(keepAlive.maxLifetimeMillis)
        writeResumeToken(resumeToken)
        writeStringMimeType(payloadMimeType.metadata)
        writeStringMimeType(payloadMimeType.data)
        writePayload(payload)
    }

    override fun StringBuilder.appendFlags() {
        appendFlag('M', payload.metadata != null)
        appendFlag('R', resumeToken != null)
        appendFlag('L', honorLease)
    }

    override fun StringBuilder.appendSelf() {
        append("\nVersion: ").append(version.toString()).append(" Honor lease: ").append(honorLease).append("\n")
        append("Keep alive: interval=").append(keepAlive.intervalMillis).append(" ms,")
        append("max lifetime=").append(keepAlive.maxLifetimeMillis).append(" ms\n")
        append("Data mime type: ").append(payloadMimeType.data).append("\n")
        append("Metadata mime type: ").append(payloadMimeType.metadata)
        appendPayload(payload)
    }
}

internal fun ByteReadPacket.readSetup(pool: ObjectPool<ChunkBuffer>, flags: Int): SetupFrame {
    val version = readVersion()
    val keepAlive = run {
        val interval = readInt()
        val maxLifetime = readInt()
        KeepAlive(intervalMillis = interval, maxLifetimeMillis = maxLifetime)
    }
    val resumeToken = if (flags check ResumeEnabledFlag) readResumeToken(pool) else null
    val payloadMimeType = run {
        val metadata = readStringMimeType()
        val data = readStringMimeType()
        PayloadMimeType(data = data, metadata = metadata)
    }
    val payload = readPayload(pool, flags)
    return SetupFrame(
        version = version,
        honorLease = flags check HonorLeaseFlag,
        keepAlive = keepAlive,
        resumeToken = resumeToken,
        payloadMimeType = payloadMimeType,
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
