/*
 * Copyright 2015-2025 the original author or authors.
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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import kotlinx.io.*

@ExperimentalMetadataApi
public interface Metadata : AutoCloseable {
    public val mimeType: MimeType
    public fun Sink.writeSelf()
}

@ExperimentalMetadataApi
public interface MetadataReader<M : Metadata> {
    public val mimeType: MimeType
    public fun Buffer.read(): M
}


@ExperimentalMetadataApi
public fun PayloadBuilder.metadata(metadata: Metadata): Unit = metadata(metadata.toBuffer())

@ExperimentalMetadataApi
public fun <M : Metadata> Buffer.read(
    reader: MetadataReader<M>,
): M = use {
    with(reader) { read() }
}

@ExperimentalMetadataApi
public fun Metadata.toBuffer(): Buffer = Buffer().apply { writeSelf() }
