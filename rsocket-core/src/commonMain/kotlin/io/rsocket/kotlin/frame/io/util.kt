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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.internal.*

internal fun ByteReadPacket.readResumeToken(pool: BufferPool): ByteReadPacket {
    val length = readShort().toInt() and 0xFFFF
    return readPacket(pool, length)
}

internal fun BytePacketBuilder.writeResumeToken(resumeToken: ByteReadPacket?) {
    resumeToken?.let {
        val length = it.remaining
        writeShort(length.toShort())
        writePacket(it)
    }
}

internal fun ByteReadPacket.readPacket(pool: BufferPool): ByteReadPacket {
    if (isEmpty) return ByteReadPacket.Empty
    return pool.buildPacket {
        writePacket(this@readPacket)
    }
}

internal fun ByteReadPacket.readPacket(pool: BufferPool, length: Int): ByteReadPacket {
    if (length == 0) return ByteReadPacket.Empty
    return pool.buildPacket {
        writePacket(this@readPacket, length)
    }
}
