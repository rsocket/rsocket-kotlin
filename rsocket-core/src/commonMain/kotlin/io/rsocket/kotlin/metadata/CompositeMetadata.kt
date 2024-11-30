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

package io.rsocket.kotlin.metadata

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.io.*
import kotlinx.io.*

@ExperimentalMetadataApi
public fun CompositeMetadata(vararg entries: Metadata): CompositeMetadata =
    DefaultCompositeMetadata(entries.map(CompositeMetadata::Entry))

@ExperimentalMetadataApi
public fun CompositeMetadata(entries: List<Metadata>): CompositeMetadata =
    DefaultCompositeMetadata(entries.map(CompositeMetadata::Entry))

@ExperimentalMetadataApi
public sealed interface CompositeMetadata : Metadata {
    public val entries: List<Entry>
    override val mimeType: MimeType get() = Reader.mimeType

    override fun Sink.writeSelf() {
        entries.forEach {
            writeMimeType(it.mimeType)
            writeInt24(it.content.size.toInt()) //write metadata length
            transferFrom(it.content) //write metadata content
        }
    }

    override fun close() {
        entries.forEach { it.content.clear() }
    }

    public class Entry(public val mimeType: MimeType, public val content: Buffer) {
        public constructor(metadata: Metadata) : this(metadata.mimeType, metadata.toBuffer())
    }

    public companion object Reader : MetadataReader<CompositeMetadata> {
        override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketCompositeMetadata
        override fun Source.read(): CompositeMetadata {
            val list = mutableListOf<Entry>()
            while (!exhausted()) {
                val type = readMimeType()
                val length = readInt24()
                val packet = readBuffer(length)
                list.add(Entry(type, packet))
            }
            return DefaultCompositeMetadata(list.toList())
        }
    }
}

@ExperimentalMetadataApi
private class DefaultCompositeMetadata(override val entries: List<CompositeMetadata.Entry>) : CompositeMetadata
