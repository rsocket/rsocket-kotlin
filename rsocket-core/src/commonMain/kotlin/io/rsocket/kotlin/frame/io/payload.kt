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

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.io.*

internal fun Buffer.readMetadata(): Buffer {
    val length = readInt24()
    return readBuffer(length)
}

internal fun Sink.writeMetadata(metadata: Buffer?) {
    metadata?.let {
        writeInt24(it.size.toInt())
        transferFrom(it)
    }
}

internal fun Buffer.readPayload(flags: Int): Payload {
    val metadata = if (flags check Flags.Metadata) readMetadata() else null
    val data = readBuffer()
    return Payload(data = data, metadata = metadata)
}

internal fun Sink.writePayload(payload: Payload) {
    writeMetadata(payload.metadata)
    transferFrom(payload.data)
}
