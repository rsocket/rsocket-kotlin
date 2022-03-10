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

class PerStreamAcceptableDataMimeTypesMetadataTest : TestWithLeakCheck {
    @Test
    fun encodeMetadata() {
        val metadata = PerStreamAcceptableDataMimeTypesMetadata(
            MimeType(110),
            MimeType.WellKnown.ApplicationAvro,
            MimeType("custom"),
            MimeType.WellKnown.ApplicationCbor,
            MimeType(120),
            MimeType("custom2"),
        )
        val decoded = metadata.readLoop(PerStreamAcceptableDataMimeTypesMetadata)
        assertEquals(MimeType.WellKnown.MessageRSocketAcceptMimeTypes, decoded.mimeType)
        assertEquals(6, decoded.types.size)
        assertEquals(
            listOf(
                MimeType(110),
                MimeType.WellKnown.ApplicationAvro,
                MimeType("custom"),
                MimeType.WellKnown.ApplicationCbor,
                MimeType(120),
                MimeType("custom2"),
            ),
            decoded.types
        )
    }
}
