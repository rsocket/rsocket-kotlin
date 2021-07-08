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

class PerStreamAcceptableDataMimeTypesMetadataTest : TestWithLeakCheck {
    @Test
    fun encodeMetadata() {
        val metadata = PerStreamAcceptableDataMimeTypesMetadata(
            ReservedMimeType(110),
            WellKnownMimeType.ApplicationAvro,
            CustomMimeType("custom"),
            WellKnownMimeType.ApplicationCbor,
            ReservedMimeType(120),
            CustomMimeType("custom2"),
        )
        val decoded = metadata.readLoop(PerStreamAcceptableDataMimeTypesMetadata)
        assertEquals(WellKnownMimeType.MessageRSocketAcceptMimeTypes, decoded.mimeType)
        assertEquals(6, decoded.types.size)
        assertEquals(
            listOf(
                ReservedMimeType(110),
                WellKnownMimeType.ApplicationAvro,
                CustomMimeType("custom"),
                WellKnownMimeType.ApplicationCbor,
                ReservedMimeType(120),
                CustomMimeType("custom2"),
            ),
            decoded.types
        )
    }
}
