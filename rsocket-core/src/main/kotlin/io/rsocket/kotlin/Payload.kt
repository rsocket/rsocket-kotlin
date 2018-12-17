/*
 * Copyright 2015-2018 the original author or authors.
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
import java.nio.charset.StandardCharsets

/** Payload of a [Frame].  */
interface Payload {
    /**
     * @return true if payload has non-null (possibly empty) metadata, false otherwise
     */
    val hasMetadata: Boolean

    /**
     * @return payload metadata. [hasMetadata]  is used to distinguish
     * null and empty value.
     */
    val metadata: ByteBuffer

    /**
     * @return payload data.
     */
    val data: ByteBuffer

    /**
     * @return string representation of payload metadata using UTF-8 character encoding.
     */
    val metadataUtf8: String
        get() = StandardCharsets.UTF_8.decode(metadata).toString()

    /**
     * @return string representation of payload data using UTF-8 character encoding.
     */
    val dataUtf8: String
        get() = StandardCharsets.UTF_8.decode(data).toString()
}
