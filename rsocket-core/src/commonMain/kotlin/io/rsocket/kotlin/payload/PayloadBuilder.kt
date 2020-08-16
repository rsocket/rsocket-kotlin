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

class PayloadBuilder
@PublishedApi
internal constructor() {
    private var data: BytePacketBuilder? = null
    private var metadata: BytePacketBuilder? = null

    @PublishedApi
    internal fun data(): BytePacketBuilder = (data ?: BytePacketBuilder().also { data = it })

    @PublishedApi
    internal fun metadata(): BytePacketBuilder = (metadata ?: BytePacketBuilder().also { metadata = it })

    inline fun data(block: BytePacketBuilder.() -> Unit): Unit = data().block()
    inline fun metadata(block: BytePacketBuilder.() -> Unit): Unit = metadata().block()

    @PublishedApi
    internal fun build(): Payload = Payload(
        data = data?.build() ?: ByteReadPacket.Empty,
        metadata = metadata?.build()
    )

    @PublishedApi
    internal fun release() {
        data?.release()
        metadata?.release()
    }
}

@Suppress("FunctionName")
inline fun Payload(config: PayloadBuilder.() -> Unit): Payload {
    val builder = PayloadBuilder()
    try {
        builder.config()
        return builder.build()
    } catch (e: Throwable) {
        builder.release()
        throw e
    }
}

fun PayloadBuilder.data(text: String): Unit = data {
    writeText(text)
}

fun PayloadBuilder.data(bytes: ByteArray): Unit = data {
    writeFully(bytes)
}

fun PayloadBuilder.metadata(text: String): Unit = metadata {
    writeText(text)
}

fun PayloadBuilder.metadata(bytes: ByteArray): Unit = metadata {
    writeFully(bytes)
}
