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

package io.rsocket.kotlin.metadata

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*

@ExperimentalMetadataApi
public interface CompositeMetadataBuilder {
    public fun add(mimeType: MimeType, metadata: ByteReadPacket)
    public fun add(metadata: Metadata)

    public fun clean()
    public fun build(): CompositeMetadata
}

@ExperimentalMetadataApi
public inline fun buildCompositeMetadata(block: CompositeMetadataBuilder.() -> Unit): CompositeMetadata {
    val builder = createCompositeMetadataBuilder()
    try {
        builder.block()
        return builder.build()
    } catch (t: Throwable) {
        builder.clean()
        throw t
    }
}

@ExperimentalMetadataApi
public inline fun PayloadBuilder.compositeMetadata(block: CompositeMetadataBuilder.() -> Unit): Unit =
    metadata(buildCompositeMetadata(block))

@PublishedApi
@ExperimentalMetadataApi
internal fun createCompositeMetadataBuilder(): CompositeMetadataBuilder = CompositeMetadataFromBuilder()

@ExperimentalMetadataApi
private class CompositeMetadataFromBuilder : CompositeMetadataBuilder, CompositeMetadata {
    private val _entries = mutableListOf<CompositeMetadata.Entry>()

    override val entries: List<CompositeMetadata.Entry> get() = _entries

    override fun add(mimeType: MimeType, metadata: ByteReadPacket) {
        _entries += CompositeMetadata.Entry(mimeType, metadata)
    }

    override fun add(metadata: Metadata) {
        _entries += CompositeMetadata.Entry(metadata)
    }

    override fun clean() {
        _entries.forEach { it.content.release() }
    }

    override fun build(): CompositeMetadata = this
}
