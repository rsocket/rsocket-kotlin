/*
 * Copyright 2015-2025 the original author or authors.
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

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.metadata.security.*
import kotlinx.io.*
import kotlin.experimental.*

internal fun Sink.writeMimeType(type: MimeType) {
    when (type) {
        is MimeTypeWithId   -> writeIdentifier(type.identifier)
        is MimeTypeWithName -> writeStringWithLength(type.text)
    }
}

internal fun Buffer.readMimeType(): MimeType = readType(
    { WellKnownMimeType(it) ?: ReservedMimeType(it) },
    { WellKnownMimeType(it) ?: CustomMimeType(it) }
)

internal fun Sink.writeAuthType(type: AuthType) {
    when (type) {
        is AuthTypeWithId   -> writeIdentifier(type.identifier)
        is AuthTypeWithName -> writeStringWithLength(type.text)
    }
}

internal fun Buffer.readAuthType(): AuthType = readType(
    { WellKnowAuthType(it) ?: ReservedAuthType(it) },
    { WellKnowAuthType(it) ?: CustomAuthType(it) }
)

private fun Sink.writeStringWithLength(text: String) {
    val typeBytes = text.encodeToByteArray()
    // The first byte indicates MIME type encoding:
    // - Values 0x00 to 0x7F represent predefined "well-known" MIME types, directly using the byte as the type ID.
    // - Values 0x80 to 0xFF denote custom MIME types, where the byte represents the length of the MIME type name that follows.
    // For custom types, the length stored (byte value minus 0x80) is adjusted by -1 when writing, and adjusted by +1 when reading,
    // mapping to an effective range of 1 to 128 characters for the MIME type name length.
    writeByte((typeBytes.size - 1).toByte())
    write(typeBytes)
}

private const val KnownTypeFlag: Byte = Byte.MIN_VALUE

private fun Sink.writeIdentifier(identifier: Byte) {
    writeByte(identifier or KnownTypeFlag)
}

private inline fun <T> Buffer.readType(
    fromIdentifier: (Byte) -> T,
    fromText: (String) -> T,
): T {
    val byte = readByte()
    return if (byte check KnownTypeFlag) {
        val identifier = byte xor KnownTypeFlag
        fromIdentifier(identifier)
    } else {
        // The first byte indicates MIME type encoding:
        // - Values 0x00 to 0x7F represent predefined "well-known" MIME types, directly using the byte as the type ID.
        // - Values 0x80 to 0xFF denote custom MIME types, where the byte represents the length of the MIME type name that follows.
        // For custom types, the length stored (byte value minus 0x80) is adjusted by -1 when writing, and adjusted by +1 when reading,
        // mapping to an effective range of 1 to 128 characters for the MIME type name length.
        val stringType = readString(byte.toLong() + 1)
        fromText(stringType)
    }
}

internal fun String.requireAscii() {
    require(all { it.code <= 0x7f }) { "String should be an ASCII encodded string" }
}
