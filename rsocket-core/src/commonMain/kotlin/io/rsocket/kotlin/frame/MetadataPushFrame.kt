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

internal class MetadataPushFrame(
    val metadata: Buffer,
) : Frame() {
    override val type: FrameType get() = FrameType.MetadataPush
    override val streamId: Int get() = 0
    override val flags: Int get() = Flags.Metadata

    override fun close() {
        metadata.close()
    }

    override fun Sink.writeSelf() {
        transferFrom(metadata)
    }

    override fun StringBuilder.appendFlags() {
        appendFlag('M', true)
    }

    override fun StringBuilder.appendSelf() {
        appendBuffer("Metadata", metadata)
    }
}

internal fun Buffer.readMetadataPush(): MetadataPushFrame =
    MetadataPushFrame(readBuffer())
