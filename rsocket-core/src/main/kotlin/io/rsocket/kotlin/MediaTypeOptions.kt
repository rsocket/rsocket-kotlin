/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin

/**
 * Configures [Payload] data and metadata MIME types
 */
class MediaTypeOptions : MediaType {
    private var dataMimeType: String = "application/binary"
    private var metadataMimeType: String = "application/binary"

    fun dataMimeType(dataMimeType: String): MediaTypeOptions {
        assertMediaType(dataMimeType)
        this.dataMimeType = dataMimeType
        return this
    }

    override fun dataMimeType(): String = dataMimeType

    fun metadataMimeType(metadataMimeType: String): MediaTypeOptions {
        assertMediaType(metadataMimeType)
        this.metadataMimeType = metadataMimeType
        return this
    }

    override fun metadataMimeType(): String = metadataMimeType

    fun copy(): MediaTypeOptions = MediaTypeOptions()
            .dataMimeType(dataMimeType)
            .metadataMimeType(metadataMimeType)

    private fun assertMediaType(mediaType: String) {
        if (mediaType.isEmpty()) {
            throw IllegalArgumentException("media type must be non-empty")
        }
    }
}