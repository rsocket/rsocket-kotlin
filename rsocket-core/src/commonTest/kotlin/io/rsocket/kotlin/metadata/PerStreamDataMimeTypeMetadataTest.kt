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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.test.*
import kotlin.test.*

class PerStreamDataMimeTypeMetadataTest : TestWithLeakCheck {
    @Test
    fun encodeReserved() {
        val metadata = PerStreamDataMimeTypeMetadata(MimeType(110))
        val decoded = metadata.readLoop(PerStreamDataMimeTypeMetadata)
        assertEquals(MimeType.WellKnown.MessageRSocketMimeType, decoded.mimeType)
        assertEquals(MimeType(110), decoded.type)
        val type = assertIs<MimeType.WithId>(decoded.type)
        assertEquals(110, type.identifier)
    }

    @Test
    fun encodeCustom() {
        val metadata = PerStreamDataMimeTypeMetadata(MimeType("custom-2"))
        val decoded = metadata.readLoop(PerStreamDataMimeTypeMetadata)
        assertEquals(MimeType.WellKnown.MessageRSocketMimeType, decoded.mimeType)
        assertEquals(MimeType("custom-2"), decoded.type)
        val type = assertIs<MimeType.WithName>(decoded.type)
        assertEquals("custom-2", type.text)
    }

    @Test
    fun encodeWellKnown() {
        val metadata = PerStreamDataMimeTypeMetadata(MimeType.WellKnown.ApplicationGraphql)
        val decoded = metadata.readLoop(PerStreamDataMimeTypeMetadata)
        assertEquals(MimeType.WellKnown.MessageRSocketMimeType, decoded.mimeType)
        assertEquals(MimeType.WellKnown.ApplicationGraphql, decoded.type)
    }
}
