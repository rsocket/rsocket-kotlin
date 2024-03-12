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
import io.rsocket.kotlin.*
import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.payload.*
import kotlinx.serialization.*
import kotlin.reflect.*

@OptIn(ExperimentalMetadataApi::class, ExperimentalSerializationApi::class)
class RpcCodec(private val format: PacketFormat) {

    @Suppress("UNCHECKED_CAST")
    fun <T> serializer(type: KType): KSerializer<T> {
        return format.serializersModule.serializer(type) as KSerializer<T>
    }

    fun <T> decodeData(serializer: KSerializer<T>, data: ByteReadPacket): T {
        return format.decodeFromPacket(serializer, data)
    }

    fun <T> encodeData(serializer: KSerializer<T>, data: T): ByteReadPacket {
        return format.encodeToPacket(serializer, data)
    }

    fun <T : RpcResponse> decodeResponsePayload(serializer: KSerializer<T>, payload: Payload): T {
        payload.metadata?.close()
        return decodeData(serializer, payload.data)
    }

    fun <T : RpcResponse> encodeResponsePayload(serializer: KSerializer<T>, response: T): Payload = buildPayload {
        data(encodeData(serializer, response))
    }

    fun <T : RpcRequest<*>> encodeRequestPayload(serializer: KSerializer<T>, request: T): Payload = buildPayload {
        data(encodeData(serializer, request))
    }

    fun <T : RpcRequest<*>> encodeInitRequestPayload(serializer: KSerializer<T>, request: T): Payload = buildPayload {
        data(encodeData(serializer, request))
        compositeMetadata {
            add(RoutingMetadata(serializer.descriptor.serialName))
        }
    }
}
