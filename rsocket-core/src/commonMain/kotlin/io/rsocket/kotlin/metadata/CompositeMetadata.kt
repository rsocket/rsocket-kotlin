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
public fun CompositeMetadata(vararg entries: Metadata): CompositeMetadata =
    DefaultCompositeMetadata(entries.map(CompositeMetadata::Entry))

@ExperimentalMetadataApi
public fun CompositeMetadata(entries: List<Metadata>): CompositeMetadata =
    DefaultCompositeMetadata(entries.map(CompositeMetadata::Entry))

@ExperimentalMetadataApi
public interface CompositeMetadata : Metadata {
    public val entries: List<Entry>
    override val mimeType: MimeType get() = Reader.mimeType

    override fun BytePacketBuilder.writeSelf() {
        entries.forEach {
            writeMimeType(it.mimeType)
            writeLength(it.content.remaining.toInt()) //write metadata length
            writePacket(it.content) //write metadata content
        }
    }

    public class Entry(public val mimeType: MimeType, public val content: ByteReadPacket) {
        public constructor(metadata: Metadata) : this(metadata.mimeType, metadata.toPacket())
    }

    public companion object Reader : MetadataReader<CompositeMetadata> {
        override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketCompositeMetadata

        @DangerousInternalIoApi
        @OptIn(ExperimentalStdlibApi::class)
        override fun ByteReadPacket.read(pool: ObjectPool<ChunkBuffer>): CompositeMetadata = DefaultCompositeMetadata(buildList {
            while (isNotEmpty) {
                val type = readMimeType()
                val length = readLength()
                val packet = readPacket(pool, length)
                add(Entry(type, packet))
            }
        })
    }
}

@ExperimentalMetadataApi
private class DefaultCompositeMetadata(override val entries: List<CompositeMetadata.Entry>) : CompositeMetadata
