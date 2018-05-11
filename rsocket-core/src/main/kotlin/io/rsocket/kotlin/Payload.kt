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
import java.nio.charset.StandardCharsets

/** Payload of a [Frame].  */
interface Payload {
    /**
     * Returns whether the payload has metadata, useful for tell if metadata is
     * empty or not present.
     *
     * @return whether payload has non-null (possibly empty) metadata
     */
    fun hasMetadata(): Boolean

    /**
     * Returns the Payload metadata. Always non-null, check [.hasMetadata] to
     * differentiate null from "".
     *
     * @return payload metadata.
     */
    val metadata: ByteBuffer

    /**
     * Returns the Payload data. Always non-null.
     *
     * @return payload data.
     */
    val data: ByteBuffer

    val metadataUtf8: String
        get() = StandardCharsets.UTF_8.decode(metadata).toString()

    val dataUtf8: String
        get() = StandardCharsets.UTF_8.decode(data).toString()
}
