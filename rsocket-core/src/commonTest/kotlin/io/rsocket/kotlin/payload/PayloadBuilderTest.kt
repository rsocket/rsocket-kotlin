/*
 * Copyright 2015-2022 the original author or authors.
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
import io.rsocket.kotlin.test.*
import kotlin.test.*

class PayloadBuilderTest : TestWithLeakCheck {

    @Test
    fun payloadCopy() {
        val payload = Payload(packet("data"), packet("metadata"))
        val copy = payload.copy()

        assertTrue(payload.data.isNotEmpty)
        assertTrue(payload.metadata?.isNotEmpty == true)
        assertTrue(copy.data.isNotEmpty)
        assertTrue(copy.metadata?.isNotEmpty == true)

        assertBytesEquals(payload.data.readBytes(), copy.data.readBytes())
        assertBytesEquals(payload.metadata?.readBytes(), copy.metadata?.readBytes())
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
