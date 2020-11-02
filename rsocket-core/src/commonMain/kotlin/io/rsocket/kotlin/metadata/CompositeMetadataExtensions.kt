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

@ExperimentalMetadataApi
public fun CompositeMetadata.Entry.hasMimeTypeOf(reader: MetadataReader<*>): Boolean = mimeType == reader.mimeType

@ExperimentalMetadataApi
@OptIn(DangerousInternalIoApi::class)
public fun <M : Metadata> CompositeMetadata.Entry.read(reader: MetadataReader<M>): M = read(ChunkBuffer.Pool, reader)

@ExperimentalMetadataApi
@OptIn(DangerousInternalIoApi::class)
public fun <M : Metadata> CompositeMetadata.Entry.readOrNull(reader: MetadataReader<M>): M? = readOrNull(ChunkBuffer.Pool, reader)

@ExperimentalMetadataApi
@DangerousInternalIoApi
public fun <M : Metadata> CompositeMetadata.Entry.read(pool: ObjectPool<ChunkBuffer>, reader: MetadataReader<M>): M {
    if (mimeType == reader.mimeType) return content.read(pool, reader)

    content.release()
    error("Expected mimeType '${reader.mimeType}' but was '$mimeType'")
}

@ExperimentalMetadataApi
@DangerousInternalIoApi
public fun <M : Metadata> CompositeMetadata.Entry.readOrNull(pool: ObjectPool<ChunkBuffer>, reader: MetadataReader<M>): M? {
    return if (mimeType == reader.mimeType) content.read(pool, reader) else null
}


@ExperimentalMetadataApi
public operator fun CompositeMetadata.contains(mimeType: MimeType): Boolean {
    return entries.any { it.mimeType == mimeType }
}

@ExperimentalMetadataApi
public operator fun CompositeMetadata.get(mimeType: MimeType): ByteReadPacket {
    return entries.first { it.mimeType == mimeType }.content
}

@ExperimentalMetadataApi
public fun CompositeMetadata.getOrNull(mimeType: MimeType): ByteReadPacket? {
    return entries.find { it.mimeType == mimeType }?.content
}

@ExperimentalMetadataApi
public fun CompositeMetadata.list(mimeType: MimeType): List<ByteReadPacket> {
    return entries.mapNotNull { if (it.mimeType == mimeType) it.content else null }
}


@ExperimentalMetadataApi
public operator fun CompositeMetadata.contains(reader: MetadataReader<*>): Boolean {
    return contains(reader.mimeType)
}

@ExperimentalMetadataApi
public operator fun <M : Metadata> CompositeMetadata.get(reader: MetadataReader<M>): M {
    return get(reader.mimeType).read(reader)
}

@ExperimentalMetadataApi
public fun <M : Metadata> CompositeMetadata.getOrNull(reader: MetadataReader<M>): M? {
    return getOrNull(reader.mimeType)?.read(reader)
}

@ExperimentalMetadataApi
public fun <M : Metadata> CompositeMetadata.list(reader: MetadataReader<M>): List<M> {
    return entries.mapNotNull { it.readOrNull(reader) }
}
