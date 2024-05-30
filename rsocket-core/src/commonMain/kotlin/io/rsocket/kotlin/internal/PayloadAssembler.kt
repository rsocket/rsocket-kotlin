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

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.payload.*

// TODO: make metadata should be fully transmitted before data
internal class PayloadAssembler : Closeable {
    // TODO: better name
    var hasPayload: Boolean = false
        private set
    private var hasMetadata: Boolean = false

    private val data = BytePacketBuilder(NoPool)
    private val metadata = BytePacketBuilder(NoPool)

    fun appendFragment(fragment: Payload) {
        hasPayload = true
        data.writePacket(fragment.data)

        val meta = fragment.metadata ?: return
        hasMetadata = true
        metadata.writePacket(meta)
    }

    fun assemblePayload(fragment: Payload): Payload {
        if (!hasPayload) return fragment

        appendFragment(fragment)

        val payload = Payload(
            data = data.build(),
            metadata = when {
                hasMetadata -> metadata.build()
                else        -> null
            }
        )
        hasMetadata = false
        hasPayload = false
        return payload
    }

    override fun close() {
        data.close()
        metadata.close()
    }

    @Suppress("DEPRECATION")
    private object NoPool : ObjectPool<ChunkBuffer> {
        override val capacity: Int get() = error("should not be called")

        override fun borrow(): ChunkBuffer {
            error("should not be called")
        }

        override fun dispose() {
            error("should not be called")
        }

        override fun recycle(instance: ChunkBuffer) {
            error("should not be called")
        }
    }
}