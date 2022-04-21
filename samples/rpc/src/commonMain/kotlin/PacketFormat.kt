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

package io.rsocket.kotlin.samples.rpc

import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*

sealed interface PacketFormat : SerialFormat {
    fun <T> encodeToPacket(serializer: SerializationStrategy<T>, value: T): ByteReadPacket
    fun <T> decodeFromPacket(deserializer: DeserializationStrategy<T>, packet: ByteReadPacket): T

    class Binary(private val format: BinaryFormat) : PacketFormat {
        override val serializersModule: SerializersModule get() = format.serializersModule
        override fun <T> encodeToPacket(serializer: SerializationStrategy<T>, value: T): ByteReadPacket {
            return ByteReadPacket(format.encodeToByteArray(serializer, value))
        }

        override fun <T> decodeFromPacket(deserializer: DeserializationStrategy<T>, packet: ByteReadPacket): T {
            return format.decodeFromByteArray(deserializer, packet.readBytes())
        }
    }

    class String(private val format: StringFormat) : PacketFormat {
        override val serializersModule: SerializersModule get() = format.serializersModule
        override fun <T> encodeToPacket(serializer: SerializationStrategy<T>, value: T): ByteReadPacket {
            return ByteReadPacket(format.encodeToString(serializer, value).encodeToByteArray())
        }

        override fun <T> decodeFromPacket(deserializer: DeserializationStrategy<T>, packet: ByteReadPacket): T {
            return format.decodeFromString(deserializer, packet.readText())
        }
    }
}
