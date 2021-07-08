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

package io.rsocket.kotlin.metadata

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*

@ExperimentalMetadataApi
public class PerStreamDataMimeTypeMetadata(public val type: MimeType) : Metadata {
    override val mimeType: MimeType get() = Reader.mimeType

    override fun BytePacketBuilder.writeSelf() {
        writeMimeType(type)
    }

    public companion object Reader : MetadataReader<PerStreamDataMimeTypeMetadata> {
        override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketMimeType
        override fun ByteReadPacket.read(pool: ObjectPool<ChunkBuffer>): PerStreamDataMimeTypeMetadata =
            PerStreamDataMimeTypeMetadata(readMimeType())
    }
}
