/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * An implementation of [Payload]. This implementation is **not** thread-safe, and hence
 * any method can not be invoked concurrently.
 */
class DefaultPayload @JvmOverloads constructor(data: ByteBuffer,
                                               private val _metadata: ByteBuffer?,
                                               private val reusable: Boolean = true)
    : Payload {
    private val dataStartPosition: Int = if (reusable) data.position() else 0
    private val metadataStartPosition: Int =
            if (reusable && _metadata != null)
                _metadata.position() else 0

    constructor(frame: Frame) : this(
            frame.data,
            if (frame.hasMetadata()) frame.metadata else null)

    constructor(data: String, metadata: String?) : this(
            data,
            StandardCharsets.UTF_8,
            metadata,
            StandardCharsets.UTF_8)

    @JvmOverloads constructor(data: String,
                              dataCharset: Charset = Charset.defaultCharset())
            : this(dataCharset.encode(data), null)

    constructor(
            data: String,
            dataCharset: Charset,
            metadata: String?,
            metaDataCharset: Charset) : this(
            dataCharset.encode(data),
            if (metadata == null) null else metaDataCharset.encode(metadata))


    constructor(data: ByteArray) : this(ByteBuffer.wrap(data), null)

    constructor(data: ByteArray, metadata: ByteArray?) : this(
            ByteBuffer.wrap(data),
            if (metadata == null) null else ByteBuffer.wrap(metadata))

    override val data = data
        get() {
            if (reusable) {
                field.position(dataStartPosition)
            }
            return field
        }

    override val metadata: ByteBuffer
        get() {
            if (_metadata == null) {
                return Frame.NULL_BYTEBUFFER
            }
            if (reusable) {
                _metadata.position(metadataStartPosition)
            }
            return _metadata
        }

    override fun hasMetadata(): Boolean = _metadata != null

    companion object {

        val EMPTY = DefaultPayload(
                Frame.NULL_BYTEBUFFER, Frame.NULL_BYTEBUFFER, false)

        /**
         * Static factory method for a text payload. Mainly looks better than
         * "new DefaultPayload(data)"
         *
         * @param data the data of the payload.
         * @return a payload.
         */
        fun text(data: String): Payload = DefaultPayload(data)

        /**
         * Static factory method for a text payload. Mainly looks better than
         * "new DefaultPayload(data, metadata)"
         *
         * @param data the data of the payload.
         * @param metadata the metadata for the payload.
         * @return a payload.
         */
        fun text(data: String, metadata: String?): Payload =
                DefaultPayload(data, metadata)
    }
}
