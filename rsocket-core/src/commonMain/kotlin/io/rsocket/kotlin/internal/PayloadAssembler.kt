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

import io.rsocket.kotlin.payload.*
import kotlinx.io.*

// TODO: make metadata should be fully transmitted before data
internal class PayloadAssembler : AutoCloseable {
    // TODO: better name
    var hasPayload: Boolean = false
        private set
    private var hasMetadata: Boolean = false

    private val data = Buffer()
    private val metadata = Buffer()

    fun appendFragment(fragment: Payload) {
        hasPayload = true
        fragment.data.transferTo(data)

        val meta = fragment.metadata ?: return
        hasMetadata = true
        meta.transferTo(metadata)
    }

    fun assemblePayload(fragment: Payload): Payload {
        if (!hasPayload) return fragment

        appendFragment(fragment)

        val payload = Payload(
            data = Buffer().also(data::transferTo),
            metadata = when {
                hasMetadata -> Buffer().also(metadata::transferTo)
                else        -> null
            }
        )
        hasMetadata = false
        hasPayload = false
        return payload
    }

    override fun close() {
        data.close()
        metadata.close()
    }
}
