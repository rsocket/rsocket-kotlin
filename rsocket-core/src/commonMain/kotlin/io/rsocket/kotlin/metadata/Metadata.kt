/*
 * Copyright 2015-2022 the original author or authors.
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
import io.rsocket.kotlin.payload.*

@ExperimentalMetadataApi
public interface Metadata : Closeable {
    public val mimeType: MimeType
    public fun BytePacketBuilder.writeSelf()
}

@ExperimentalMetadataApi
public interface MetadataReader<M : Metadata> {
    public val mimeType: MimeType
    public fun ByteReadPacket.read(pool: ObjectPool<ChunkBuffer>): M
}


@ExperimentalMetadataApi
public fun PayloadBuilder.metadata(metadata: Metadata): Unit = metadata(metadata.toPacket())

@ExperimentalMetadataApi
public fun <M : Metadata> ByteReadPacket.read(
    reader: MetadataReader<M>,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
): M = use {
    with(reader) { read(pool) }
}

@ExperimentalMetadataApi
public fun Metadata.toPacket(pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool): ByteReadPacket =
    buildPacket(pool) { writeSelf() }
