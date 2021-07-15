/*
 * Copyright 2015-2020 the original author or authors.
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

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import kotlin.test.*

class PerStreamDataMimeTypeMetadataTest : TestWithLeakCheck {
    @Test
    fun encodeReserved() {
        val metadata = PerStreamDataMimeTypeMetadata(ReservedMimeType(110))
        val decoded = metadata.readLoop(PerStreamDataMimeTypeMetadata)
        assertEquals(WellKnownMimeType.MessageRSocketMimeType, decoded.mimeType)
        assertEquals(ReservedMimeType(110), decoded.type)
    }

    @Test
    fun encodeCustom() {
        val metadata = PerStreamDataMimeTypeMetadata(CustomMimeType("custom-2"))
        val decoded = metadata.readLoop(PerStreamDataMimeTypeMetadata)
        assertEquals(WellKnownMimeType.MessageRSocketMimeType, decoded.mimeType)
        assertEquals(CustomMimeType("custom-2"), decoded.type)
    }

    @Test
    fun encodeWellKnown() {
        val metadata = PerStreamDataMimeTypeMetadata(WellKnownMimeType.ApplicationGraphql)
        val decoded = metadata.readLoop(PerStreamDataMimeTypeMetadata)
        assertEquals(WellKnownMimeType.MessageRSocketMimeType, decoded.mimeType)
        assertEquals(WellKnownMimeType.ApplicationGraphql, decoded.type)
    }
}
