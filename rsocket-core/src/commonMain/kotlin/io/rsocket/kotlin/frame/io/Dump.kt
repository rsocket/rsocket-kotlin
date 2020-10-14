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
import io.rsocket.kotlin.payload.*
import kotlin.native.concurrent.*

@SharedImmutable
private val digits = "0123456789abcdef".toCharArray()

@SharedImmutable
private const val divider = "+--------+-------------------------------------------------+----------------+"

@SharedImmutable
private const val header = """
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
"""

//         +-------------------------------------------------+
//         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
//+--------+-------------------------------------------------+----------------+
//|00000000| 74 65 73 74 2d 64 61 74 61 20 74 65 73 74 2d 64 |test-data test-d|
//|00000001| 61 74 61 20 74 65 73 74 2d 64 61 74 61          |ata test-data   |
//+--------+-------------------------------------------------+----------------+
internal fun StringBuilder.appendPacket(packet: ByteReadPacket) {

    var rowIndex = 0
    var byteIndex = 0
    val bytes = ByteArray(32)

    fun appendBytesAsString() {
        val string = bytes.decodeToString()
        if (byteIndex == 16) {
            append(" |").append(string)
        } else {
            val leftSpace = "   ".repeat(16 - byteIndex)
            val space = " ".repeat(16 - byteIndex)
            append(leftSpace).append(" |").append(string).append(space)
        }
        append("|")
    }

    fun appendRowIndex() {
        //hex
        val string = rowIndex.toString(16)
        append("\n|").append("0".repeat(7 - string.length)).append(string).append("0|")
    }

    append(header)
    append(divider)

    appendRowIndex()

    val copy = packet.copy()
    while (copy.isNotEmpty) {
        val byte = copy.readByte()
        val b = byte.toInt() and 0xff

        bytes[byteIndex++] = byte

        append(" ")
        append(digits[b shr 4])
        append(digits[b and 0x0f])

        if (byteIndex == 16) {
            appendBytesAsString()
            byteIndex = 0
            bytes.fill(0)

            rowIndex++
            appendRowIndex()
        }
    }
    if (byteIndex != 0) appendBytesAsString()
    append("\n")
    append(divider)
}

internal fun StringBuilder.appendPacket(tag: String, packet: ByteReadPacket) {
    append("\n").append(tag)
    if (packet.remaining > 0) {
        append("(length=").append(packet.remaining).append("):")
        appendPacket(packet)
    } else {
        append(": Empty")
    }
}

internal fun StringBuilder.appendPayload(payload: Payload) {
    if (payload.metadata != null) appendPacket("Metadata", payload.metadata)
    appendPacket("Data", payload.data)
}

internal fun Int.toBinaryString(): String {
    val string = toString(2)
    return "0".repeat(9 - string.length) + string
}
