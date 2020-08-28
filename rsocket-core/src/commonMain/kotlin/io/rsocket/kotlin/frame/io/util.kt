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

package io.rsocket.kotlin.frame.io

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import kotlin.time.*

fun ByteReadPacket.readResumeToken(): ByteReadPacket {
    val length = readShort().toInt() and 0xFFFF
    return readPacket(length)
}

fun BytePacketBuilder.writeResumeToken(resumeToken: ByteReadPacket?) {
    resumeToken?.let {
        val length = it.remaining
        writeShort(length.toShort())
        writePacket(it)
    }
}

fun ByteReadPacket.readMimeType(): String {
    val length = readByte().toInt()
    return readText(max = length)
}

fun BytePacketBuilder.writeMimeType(mimeType: String) {
    val bytes = mimeType.encodeToByteArray() //TODO check
    writeByte(bytes.size.toByte())
    writeFully(bytes)
}

fun ByteReadPacket.readPayloadMimeType(): PayloadMimeType {
    val metadata = readMimeType()
    val data = readMimeType()
    return PayloadMimeType(data = data, metadata = metadata)
}

fun BytePacketBuilder.writePayloadMimeType(payloadMimeType: PayloadMimeType) {
    writeMimeType(payloadMimeType.metadata)
    writeMimeType(payloadMimeType.data)
}

@OptIn(ExperimentalTime::class)
fun ByteReadPacket.readMillis(): Duration = readInt().milliseconds

@OptIn(ExperimentalTime::class)
fun BytePacketBuilder.writeMillis(duration: Duration) {
    writeInt(duration.toInt(DurationUnit.MILLISECONDS))
}

fun ByteReadPacket.readKeepAlive(): KeepAlive {
    val interval = readMillis()
    val maxLifetime = readMillis()
    return KeepAlive(interval = interval, maxLifetime = maxLifetime)
}

fun BytePacketBuilder.writeKeepAlive(keepAlive: KeepAlive) {
    writeMillis(keepAlive.interval)
    writeMillis(keepAlive.maxLifetime)
}

fun ByteReadPacket.readPacket(): ByteReadPacket {
    if (isEmpty) return ByteReadPacket.Empty
    return buildPacket {
        writePacket(this@readPacket)
    }
}

fun ByteReadPacket.readPacket(length: Int): ByteReadPacket {
    if (length == 0) return ByteReadPacket.Empty
    return buildPacket {
        writePacket(this@readPacket, length)
    }
}
