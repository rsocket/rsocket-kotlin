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

package io.rsocket.kotlin

import io.ktor.utils.io.core.*

@OptIn(ExperimentalStdlibApi::class)
public interface RSocketPayload : AutoCloseable {
    // data and metadata are single use
    public val data: ByteReadPacket
    public val metadata: RSocketMetadata?
}

@OptIn(ExperimentalStdlibApi::class)
public interface RSocketMetadata : AutoCloseable {
    public val content: ByteReadPacket
}

public fun RSocketPayload(data: ByteReadPacket, metadata: ByteReadPacket? = null): RSocketPayload = TODO()

public inline fun RSocketPayload(block: RSocketPayloadBuilder.() -> Unit): RSocketPayload = TODO()

@OptIn(ExperimentalStdlibApi::class)
public sealed class RSocketPayloadBuilder : AutoCloseable {
    public fun data(value: ByteReadPacket) {}
    public fun metadata(value: ByteReadPacket?) {}
}

public inline fun RSocketPayloadBuilder.data(block: BytePacketBuilder.() -> Unit): Unit = data(buildPacket(block = block))
public fun RSocketPayloadBuilder.data(value: String): Unit = data { writeText(value) }
public fun RSocketPayloadBuilder.data(value: ByteArray): Unit = data { writeFully(value) }

public inline fun RSocketPayloadBuilder.metadata(block: BytePacketBuilder.() -> Unit): Unit = metadata(buildPacket(block = block))
public fun RSocketPayloadBuilder.metadata(value: String?): Unit = metadata { writeText(value) }
public fun RSocketPayloadBuilder.metadata(value: ByteArray?): Unit = metadata { writeFully(value) }

public interface RSocketPayloadDecoder<P : RSocketPayload, M : RSocketMetadata> {
    public suspend fun decode(data: ByteReadPacket, metadata: M?): P
}

public interface RSocketMetadataDecoder<M : RSocketMetadata> {
    public suspend fun decode(metadata: ByteReadPacket): M
}
