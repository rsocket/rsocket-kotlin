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

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.frame.io.*
import kotlinx.io.*

internal class LeaseFrame(
    val ttl: Int,
    val numberOfRequests: Int,
    val metadata: Buffer?,
) : Frame() {
    override val type: FrameType get() = FrameType.Lease
    override val streamId: Int get() = 0
    override val flags: Int get() = if (metadata != null) Flags.Metadata else 0

    override fun close() {
        metadata?.close()
    }

    override fun Sink.writeSelf() {
        writeInt(ttl)
        writeInt(numberOfRequests)
        writeMetadata(metadata)
    }

    override fun StringBuilder.appendFlags() {
        appendFlag('M', metadata != null)
    }

    override fun StringBuilder.appendSelf() {
        append("\nNumber of requests: ").append(numberOfRequests)
        if (metadata != null) appendBuffer("Metadata", metadata)
    }
}

internal fun Buffer.readLease(flags: Int): LeaseFrame {
    val ttl = readInt()
    val numberOfRequests = readInt()
    val metadata = if (flags check Flags.Metadata) readMetadata() else null
    return LeaseFrame(ttl, numberOfRequests, metadata)
}
