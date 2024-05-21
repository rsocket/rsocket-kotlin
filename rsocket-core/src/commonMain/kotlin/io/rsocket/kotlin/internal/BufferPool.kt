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
import kotlin.jvm.*

@Suppress("DEPRECATION")
@JvmInline
public value class BufferPool(
    @PublishedApi
    internal val pool: ObjectPool<ChunkBuffer>,
) {
    public inline fun buildPacket(block: BytePacketBuilder.() -> Unit): ByteReadPacket {
        val builder = BytePacketBuilder(pool)
        try {
            block(builder)
            return builder.build()
        } catch (t: Throwable) {
            builder.close()
            throw t
        }
    }

    public companion object {
        public val Default: BufferPool = BufferPool(ChunkBuffer.Pool)
    }
}
