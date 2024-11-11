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

package io.rsocket.kotlin.payload

import io.rsocket.kotlin.frame.io.*
import kotlinx.io.*

public sealed interface PayloadBuilder : AutoCloseable {
    public fun data(value: Buffer)
    public fun metadata(value: Buffer)
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

public inline fun PayloadBuilder.data(block: Sink.() -> Unit): Unit = data(Buffer().apply(block))
public fun PayloadBuilder.data(value: String): Unit = data { writeString(value) }
public fun PayloadBuilder.data(value: ByteArray): Unit = data { write(value) }

public inline fun PayloadBuilder.metadata(block: Sink.() -> Unit): Unit = metadata(Buffer().apply(block))

public fun PayloadBuilder.metadata(value: String): Unit = metadata { writeString(value) }
public fun PayloadBuilder.metadata(value: ByteArray): Unit = metadata { write(value) }


@PublishedApi
internal class PayloadFromBuilder : PayloadBuilder, Payload {
    private var hasData = false
    private var hasMetadata = false

    override var data: Buffer = EmptyBuffer
        private set
    override var metadata: Buffer? = null
        private set

    override fun data(value: Buffer) {
        if (hasData) {
            value.close()
            error("Data already provided")
        }
        data = value
        hasData = true
    }

    override fun metadata(value: Buffer) {
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
