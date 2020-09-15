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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.payload.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.jvm.*

//just stub
val ConfiguredProtoBuf = ProtoBuf

@ExperimentalSerializationApi
inline fun <reified T> ProtoBuf.decodeFromPayload(payload: Payload): T = decodeFromByteArray(payload.data.readBytes())

@ExperimentalSerializationApi
inline fun <reified T> ProtoBuf.encodeToPayload(route: String, value: T): Payload {
    return kotlin.runCatching { //TODO some ktor issue...
        Payload {
            data(encodeToByteArray(value))
            metadata(route)
        }
    }.getOrNull() ?: Payload {
        data(encodeToByteArray(value))
        metadata(route)
    }
}

@ExperimentalSerializationApi
inline fun <reified T> ProtoBuf.encodeToPayload(value: T): Payload = Payload(encodeToByteArray(value))

@ExperimentalSerializationApi
inline fun <reified I, reified O> ProtoBuf.decoding(payload: Payload, block: (I) -> O): Payload =
    encodeToPayload(decodeFromPayload<I>(payload).let(block))

@ExperimentalSerializationApi
@JvmName("decoding2")
inline fun <reified I> ProtoBuf.decoding(payload: Payload, block: (I) -> Unit): Payload {
    decodeFromPayload<I>(payload).let(block)
    return Payload.Empty
}
