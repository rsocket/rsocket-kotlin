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

package io.rsocket.kotlin.metadata

import io.rsocket.kotlin.test.*
import kotlin.test.*

class RoutingMetadataTest : TestWithLeakCheck {
    @Test
    fun encodeMetadata() {
        val tags = listOf("ws://localhost:8080/rsocket", "x".repeat(200))
        val metadata = RoutingMetadata(tags)
        val decodedMetadata = metadata.readLoop(RoutingMetadata)
        assertEquals(tags, decodedMetadata.tags)
    }

    @Test
    fun failOnEmptyTag() {
        assertFailsWith(IllegalArgumentException::class) {
            RoutingMetadata(listOf("", "tag"))
        }
    }

    @Test
    fun failOnLongTag() {
        assertFailsWith(IllegalArgumentException::class) {
            RoutingMetadata(listOf("tag", "t".repeat(256)))
        }
    }
}
