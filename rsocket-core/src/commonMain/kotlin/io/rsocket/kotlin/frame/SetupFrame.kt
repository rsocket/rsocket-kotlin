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
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import kotlin.time.*

private const val HonorLeaseFlag = 64
private const val ResumeEnabledFlag = 128

internal class SetupFrame(
    val version: Version, //TODO check
    val honorLease: Boolean,
    val keepAlive: KeepAlive,
    val resumeToken: ByteReadPacket?,
    val payloadMimeType: PayloadMimeType,
    val payload: Payload,
) : Frame(FrameType.Setup) {
    override val streamId: Int get() = 0
    override val flags: Int
        get() {
            var flags = 0
            if (honorLease) flags = flags or HonorLeaseFlag
            if (resumeToken != null) flags = flags or ResumeEnabledFlag
            if (payload.metadata != null) flags = flags or Flags.Metadata
            return flags
        }

    override fun release() {
        resumeToken?.release()
        payload.release()
    }

    override fun BytePacketBuilder.writeSelf() {
        writeVersion(version)
        writeKeepAlive(keepAlive)
        writeResumeToken(resumeToken)
        writePayloadMimeType(payloadMimeType)
        writePayload(payload)
    }

    override fun StringBuilder.appendFlags() {
        appendFlag('M', payload.metadata != null)
        appendFlag('R', resumeToken != null)
        appendFlag('L', honorLease)
    }

    override fun StringBuilder.appendSelf() {
        append("\nVersion: ").append(version.toString()).append(" Honor lease: ").append(honorLease).append("\n")
        append("Keep alive: interval=").append(keepAlive.interval).append(", max lifetime=").append(keepAlive.maxLifetime).append("\n")
        append("Data mime type: ").append(payloadMimeType.data).append("\n")
        append("Metadata mime type: ").append(payloadMimeType.metadata)
        appendPayload(payload)
    }
}

internal fun ByteReadPacket.readSetup(pool: BufferPool, flags: Int): SetupFrame {
    val version = readVersion()
    val keepAlive = readKeepAlive()
    val resumeToken = if (flags check ResumeEnabledFlag) readResumeToken(pool) else null
    val payloadMimeType = readPayloadMimeType()
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

private fun ByteReadPacket.readMimeType(): String {
    val length = readByte().toInt()
    return readTextExactBytes(length)
}

private fun BytePacketBuilder.writeMimeType(mimeType: String) {
    val bytes = mimeType.encodeToByteArray() //TODO check
    writeByte(bytes.size.toByte())
    writeFully(bytes)
}

private fun ByteReadPacket.readPayloadMimeType(): PayloadMimeType {
    val metadata = readMimeType()
    val data = readMimeType()
    return PayloadMimeType(data = data, metadata = metadata)
}

private fun BytePacketBuilder.writePayloadMimeType(payloadMimeType: PayloadMimeType) {
    writeMimeType(payloadMimeType.metadata)
    writeMimeType(payloadMimeType.data)
}

@OptIn(ExperimentalTime::class)
private fun ByteReadPacket.readMillis(): Duration = readInt().milliseconds

@OptIn(ExperimentalTime::class)
private fun BytePacketBuilder.writeMillis(duration: Duration) {
    writeInt(duration.toInt(DurationUnit.MILLISECONDS))
}

private fun ByteReadPacket.readKeepAlive(): KeepAlive {
    val interval = readMillis()
    val maxLifetime = readMillis()
    return KeepAlive(interval = interval, maxLifetime = maxLifetime)
}

private fun BytePacketBuilder.writeKeepAlive(keepAlive: KeepAlive) {
    writeMillis(keepAlive.interval)
    writeMillis(keepAlive.maxLifetime)
}
