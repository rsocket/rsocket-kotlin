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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.internal.*

@ExperimentalMetadataApi
public fun RoutingMetadata(vararg tags: String): RoutingMetadata = RoutingMetadata(tags.toList())

@ExperimentalMetadataApi
public class RoutingMetadata(public val tags: List<String>) : Metadata {
    init {
        tags.forEach {
            require(it.length in 1..255) { "Tag length must be in range 1..255 but was '${it.length}'" }
        }
    }

    override val mimeType: MimeType get() = Reader.mimeType

    override fun BytePacketBuilder.writeSelf() {
        tags.forEach {
            val bytes = it.encodeToByteArray()
            writeByte(bytes.size.toByte())
            writeFully(bytes)
        }
    }

    override fun close(): Unit = Unit

    public companion object Reader : MetadataReader<RoutingMetadata> {
        override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketRouting
        override fun ByteReadPacket.read(pool: BufferPool): RoutingMetadata {
            val list = mutableListOf<String>()
            while (isNotEmpty) {
                val length = readByte().toInt() and 0xFF
                list.add(readTextExactBytes(length))
            }
            return RoutingMetadata(list.toList())
        }
    }
}
