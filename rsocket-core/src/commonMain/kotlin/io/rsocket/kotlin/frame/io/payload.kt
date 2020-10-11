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

internal fun ByteReadPacket.readMetadata(pool: BufferPool): ByteReadPacket {
    val length = readLength()
    return readPacket(pool, length)
}

internal fun BytePacketBuilder.writeMetadata(metadata: ByteReadPacket?) {
    metadata?.let {
        writeLength(it.remaining.toInt())
        writePacket(it)
    }
}

internal fun ByteReadPacket.readPayload(pool: BufferPool, flags: Int): Payload {
    val metadata = if (flags check Flags.Metadata) readMetadata(pool) else null
    val data = readPacket(pool)
    return Payload(data = data, metadata = metadata)
}

internal fun BytePacketBuilder.writePayload(payload: Payload) {
    writeMetadata(payload.metadata)
    writePacket(payload.data)
}
