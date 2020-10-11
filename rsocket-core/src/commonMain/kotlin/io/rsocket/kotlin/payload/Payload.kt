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

package io.rsocket.kotlin.payload

import io.ktor.utils.io.core.*

class Payload(
    val data: ByteReadPacket,
    val metadata: ByteReadPacket? = null,
) : Closeable {

    fun copy(): Payload = Payload(data.copy(), metadata?.copy())

    fun release() {
        data.release()
        metadata?.release()
    }

    override fun close() {
        release()
    }

    companion object {
        val Empty = Payload(ByteReadPacket.Empty)
    }
}

fun Payload(data: String, metadata: String? = null): Payload = Payload(
    data = buildPacket { writeText(data) },
    metadata = metadata?.let { buildPacket { writeText(it) } }
)

@Suppress("FunctionName")
fun Payload(data: ByteArray, metadata: ByteArray? = null): Payload = Payload(
    data = buildPacket { writeFully(data) },
    metadata = metadata?.let { buildPacket { writeFully(it) } }
)

/**
 * Wrap data and metadata arrays without copying them.
 * Changes in input arrays will change payload data, same as reading from payload will change input arrays.
 */
fun Payload.Companion.wrap(data: ByteArray, metadata: ByteArray? = null): Payload = Payload(
    data = ByteReadPacket(data),
    metadata = metadata?.let { ByteReadPacket(it) }
)
