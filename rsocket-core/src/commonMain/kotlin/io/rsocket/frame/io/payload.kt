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
import io.ktor.utils.io.core.internal.*
import io.rsocket.payload.*

fun Input.readMetadata(): ByteReadPacket {
    val length = readLength()
    return readPacket(length)
}

fun Output.writeMetadata(metadata: ByteReadPacket?) {
    metadata?.let {
        writeLength(it.remaining.toInt())
        writePacket(it)
    }
}

fun Input.readPayload(flags: Int): Payload {
    val metadata = if (flags check Flags.Metadata) readMetadata() else null
    val data = readPacket()
    return Payload(data, metadata)
}

fun Output.writePayload(payload: Payload) {
    writeMetadata(payload.metadata)
    writePacket(payload.data)
}

@OptIn(DangerousInternalIoApi::class)
fun Input.readPacket(): ByteReadPacket {
    if (endOfInput) return ByteReadPacket.Empty
    return buildPacket {
        writeWhileSize {
            readAvailable(it)
        }
    }
}

@OptIn(DangerousInternalIoApi::class)
fun Input.readPacket(length: Int): ByteReadPacket {
    if (length == 0) return ByteReadPacket.Empty
    return buildPacket {
        var remaining = length
        writeWhileSize {
            val count = minOf(remaining, it.writeRemaining)
            remaining -= readAvailable(it, count)
            remaining
        }
    }
}
