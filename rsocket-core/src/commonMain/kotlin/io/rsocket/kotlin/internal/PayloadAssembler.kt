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

import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.io.*

// TODO: make metadata should be fully transmitted before data
internal class PayloadAssembler : AutoCloseable {
    // TODO: better name
    val hasPayload: Boolean
        get() = data != null

    private var data: Buffer? = null
    private var metadata: Buffer? = null

    fun appendFragment(fragment: Payload) {
        fragment.data.transferTo(data ?: Buffer().also { data = it })
        fragment.metadata?.transferTo(metadata ?: Buffer().also { metadata = it })
    }

    fun assemblePayload(fragment: Payload): Payload {
        if (!hasPayload) return fragment

        appendFragment(fragment)

        val payload = Payload(
            data = data ?: EmptyBuffer, // probably never happens
            metadata = metadata
        )
        data = null
        metadata = null
        return payload
    }

    override fun close() {
        data?.clear()
        data = null
        metadata?.clear()
        metadata = null
    }
}