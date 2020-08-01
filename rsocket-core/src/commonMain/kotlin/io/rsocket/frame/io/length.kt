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

package io.rsocket.frame.io

import io.ktor.utils.io.core.*

private const val lengthMask: Int = 0xFFFFFF.inv()

fun Input.readLength(): Int {
    val b = readByte().toInt() and 0xFF shl 16
    val b1 = readByte().toInt() and 0xFF shl 8
    val b2 = readByte().toInt() and 0xFF
    return b or b1 or b2
}

fun Output.writeLength(length: Int) {
    require(length and lengthMask == 0) { "Length is larger than 24 bits" }
    writeByte((length shr 16).toByte())
    writeByte((length shr 8).toByte())
    writeByte(length.toByte())
}
