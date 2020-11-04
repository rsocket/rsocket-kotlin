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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.metadata.security.*
import kotlin.experimental.*

internal fun BytePacketBuilder.writeMimeType(type: MimeType) {
    when (type) {
        is MimeTypeWithId   -> writeIdentifier(type.identifier)
        is MimeTypeWithName -> writeTextWithLength(type.text)
        else                -> error("Unknown mime type")
    }
}

internal fun ByteReadPacket.readMimeType(): MimeType = readType(
    { WellKnownMimeType(it) ?: ReservedMimeType(it) },
    { WellKnownMimeType(it) ?: CustomMimeType(it) }
)

internal fun BytePacketBuilder.writeAuthType(type: AuthType) {
    when (type) {
        is AuthTypeWithId   -> writeIdentifier(type.identifier)
        is AuthTypeWithName -> writeTextWithLength(type.text)
        else                -> error("Unknown mime type")
    }
}

internal fun ByteReadPacket.readAuthType(): AuthType = readType(
    { WellKnowAuthType(it) ?: ReservedAuthType(it) },
    { WellKnowAuthType(it) ?: CustomAuthType(it) }
)

private fun BytePacketBuilder.writeTextWithLength(text: String) {
    val typeBytes = text.encodeToByteArray()
    writeByte(typeBytes.size.toByte()) //write length
    writeFully(typeBytes) //write mime type
}

private const val KnownTypeFlag: Byte = Byte.MIN_VALUE

private fun BytePacketBuilder.writeIdentifier(identifier: Byte) {
    writeByte(identifier or KnownTypeFlag)
}

private inline fun <T> ByteReadPacket.readType(
    fromIdentifier: (Byte) -> T,
    fromText: (String) -> T,
): T {
    val byte = readByte()
    return if (byte check KnownTypeFlag) {
        val identifier = byte xor KnownTypeFlag
        fromIdentifier(identifier)
    } else {
        val stringType = readTextExactBytes(byte.toInt())
        fromText(stringType)
    }
}

internal fun String.requireAscii() {
    require(all { it.toInt() <= 0x7f }) { "String should be an ASCII encodded string" }
}
