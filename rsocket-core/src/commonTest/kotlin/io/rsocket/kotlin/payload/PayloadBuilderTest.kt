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

import io.rsocket.kotlin.test.*
import kotlinx.io.*
import kotlin.test.*

class PayloadBuilderTest {

    @Test
    fun payloadCopy() {
        val payload = Payload(packet("data"), packet("metadata"))
        val copy = payload.copy()

        assertTrue(!payload.data.exhausted())
        assertTrue(payload.metadata?.let { !it.exhausted() } == true)
        assertTrue(!copy.data.exhausted())
        assertTrue(copy.metadata?.let { !it.exhausted() } == true)

        assertBytesEquals(payload.data.readByteArray(), copy.data.readByteArray())
        assertBytesEquals(payload.metadata?.readByteArray(), copy.metadata?.readByteArray())
    }

    @Test
    fun payloadclose() {
        Payload(packet("data"), packet("metadata")).close()
    }

    @Test
    fun failOnBuilderWithNoData() {
        assertFailsWith(IllegalStateException::class) {
            buildPayload {
                metadata(packet("metadata"))
            }
        }
    }

    @Test
    fun failOnBuilderDataReassignment() {
        assertFailsWith(IllegalStateException::class) {
            buildPayload {
                data(packet("data"))
                data(packet("data2"))
            }
        }
    }

    @Test
    fun failOnBuilderMetadataReassignment() {
        assertFailsWith(IllegalStateException::class) {
            buildPayload {
                metadata(packet("data"))
                metadata(packet("data2"))
            }
        }
    }
}
