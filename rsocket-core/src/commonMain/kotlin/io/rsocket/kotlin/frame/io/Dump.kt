/*
 * Copyright 2015-2024 the original author or authors.
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

import io.rsocket.kotlin.payload.*
import kotlinx.io.*

private val digits = "0123456789abcdef".toCharArray()

private const val divider = "+--------+-------------------------------------------------+----------------+"

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
internal fun StringBuilder.appendBuffer(buffer: Buffer) {

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

    val copy = buffer.copy()
    while (!copy.exhausted()) {
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

internal fun StringBuilder.appendBuffer(tag: String, buffer: Buffer) {
    append("\n").append(tag)
    if (buffer.size > 0) {
        append("(length=").append(buffer.size).append("):")
        appendBuffer(buffer)
    } else {
        append(": Empty")
    }
}

internal fun StringBuilder.appendPayload(payload: Payload) {
    val metadata = payload.metadata
    if (metadata != null) appendBuffer("Metadata", metadata)
    appendBuffer("Data", payload.data)
}

internal fun Int.toBinaryString(): String {
    val string = toString(2)
    return "0".repeat(9 - string.length) + string
}
