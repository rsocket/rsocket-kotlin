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

public sealed interface PayloadBuilder : Closeable {
    public fun data(value: ByteReadPacket)
    public fun metadata(value: ByteReadPacket)
}

public inline fun buildPayload(block: PayloadBuilder.() -> Unit): Payload {
    val builder = PayloadFromBuilder()
    try {
        builder.block()
        return builder.build()
    } catch (t: Throwable) {
        builder.close()
        throw t
    }
}

public inline fun PayloadBuilder.data(block: BytePacketBuilder.() -> Unit): Unit = data(buildPacket(block = block))
public fun PayloadBuilder.data(value: String): Unit = data { writeText(value) }
public fun PayloadBuilder.data(value: ByteArray): Unit = data { writeFully(value) }

public inline fun PayloadBuilder.metadata(block: BytePacketBuilder.() -> Unit): Unit = metadata(buildPacket(block = block))
public fun PayloadBuilder.metadata(value: String): Unit = metadata { writeText(value) }
public fun PayloadBuilder.metadata(value: ByteArray): Unit = metadata { writeFully(value) }


@PublishedApi
internal class PayloadFromBuilder : PayloadBuilder, Payload {
    private var hasData = false
    private var hasMetadata = false

    override var data: ByteReadPacket = ByteReadPacket.Empty
        private set
    override var metadata: ByteReadPacket? = null
        private set

    override fun data(value: ByteReadPacket) {
        if (hasData) {
            value.close()
            error("Data already provided")
        }
        data = value
        hasData = true
    }

    override fun metadata(value: ByteReadPacket) {
        if (hasMetadata) {
            value.close()
            error("Metadata already provided")
        }
        metadata = value
        hasMetadata = true
    }

    fun build(): Payload {
        check(hasData) { "Data is required" }
        return this
    }
}
