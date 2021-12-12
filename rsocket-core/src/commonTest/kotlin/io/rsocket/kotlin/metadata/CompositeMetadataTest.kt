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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.test.*
import kotlin.test.*

class CompositeMetadataTest : TestWithLeakCheck {

    @Test
    fun decodeEntryHasNoContent() {
        val cm = buildCompositeMetadata {
            add(CustomMimeType("w"), ByteReadPacket.Empty)
        }

        val decoded = cm.readLoop(CompositeMetadata)

        assertEquals(1, decoded.entries.size)
        val entry = decoded.entries.first()
        assertEquals(CustomMimeType("w"), entry.mimeType)
        assertEquals(0, entry.content.remaining)
    }

    @Test
    fun decodeMultiple() {
        val cm = buildCompositeMetadata {
            add(CustomMimeType("custom"), packet("custom metadata"))
            add(ReservedMimeType(120), packet("reserved metadata"))
            add(WellKnownMimeType.ApplicationAvro, packet("avro metadata"))
        }
        val decoded = cm.readLoop(CompositeMetadata)

        assertEquals(3, decoded.entries.size)
        decoded.entries[0].let { custom ->
            assertEquals(CustomMimeType("custom"), custom.mimeType)
            assertEquals("custom metadata", custom.content.readText())
        }
        decoded.entries[1].let { reserved ->
            assertEquals(ReservedMimeType(120), reserved.mimeType)
            assertEquals("reserved metadata", reserved.content.readText())
        }
        decoded.entries[2].let { known ->
            assertEquals(WellKnownMimeType.ApplicationAvro, known.mimeType)
            assertEquals("avro metadata", known.content.readText())
        }
    }

    @Test
    fun failOnMimeTypeWithNoMimeLength() {
        val packet = packet {
            writeByte(120)
        }
        assertFails {
            packet.read(CompositeMetadata, InUseTrackingPool)
        }
    }

    @Test
    fun testContains() {
        val cm = buildCompositeMetadata {
            add(CustomMimeType("custom"), packet("custom metadata"))
            add(ReservedMimeType(120), packet("reserved metadata"))
            add(WellKnownMimeType.ApplicationAvro, packet("avro metadata"))
        }
        val decoded = cm.readLoop(CompositeMetadata)

        assertTrue(CustomMimeType("custom") in decoded)
        assertTrue(CustomMimeType("custom2") !in decoded)

        assertTrue(ReservedMimeType(120) in decoded)
        assertTrue(ReservedMimeType(110) !in decoded)

        assertTrue(WellKnownMimeType.ApplicationAvro in decoded)
        assertTrue(WellKnownMimeType.MessageRSocketRouting !in decoded)

        decoded.entries.forEach { it.content.close() }
    }

    @Test
    fun testGet() {
        val cm = buildCompositeMetadata {
            add(CustomMimeType("custom"), packet("custom metadata"))
            add(ReservedMimeType(120), packet("reserved metadata"))
            add(WellKnownMimeType.ApplicationAvro, packet("avro metadata"))
        }
        val decoded = cm.readLoop(CompositeMetadata)

        assertEquals("custom metadata", decoded[CustomMimeType("custom")].readText())
        assertEquals("reserved metadata", decoded[ReservedMimeType(120)].readText())
        assertEquals("avro metadata", decoded[WellKnownMimeType.ApplicationAvro].readText())
    }

    @Test
    fun testGetOrNull() {
        val cm = buildCompositeMetadata {
            add(CustomMimeType("custom"), packet("custom metadata"))
            add(ReservedMimeType(120), packet("reserved metadata"))
            add(WellKnownMimeType.ApplicationAvro, packet("avro metadata"))
        }
        val decoded = cm.readLoop(CompositeMetadata)

        assertNull(decoded.getOrNull(ReservedMimeType(121)))
        assertNull(decoded.getOrNull(CustomMimeType("custom2")))
        assertNull(decoded.getOrNull(WellKnownMimeType.MessageRSocketRouting))

        assertEquals("custom metadata", decoded.getOrNull(CustomMimeType("custom"))?.readText())
        assertEquals("reserved metadata", decoded.getOrNull(ReservedMimeType(120))?.readText())
        assertEquals("avro metadata", decoded.getOrNull(WellKnownMimeType.ApplicationAvro)?.readText())
    }

    @Test
    fun testList() {
        val cm = buildCompositeMetadata {
            add(CustomMimeType("custom"), packet("custom metadata - 1"))
            add(ReservedMimeType(120), packet("reserved metadata - 1"))
            add(ReservedMimeType(120), packet("reserved metadata - 2"))
            add(WellKnownMimeType.MessageRSocketRouting, packet("routing metadata"))
            add(CustomMimeType("custom"), packet("custom metadata - 2"))
        }
        val decoded = cm.readLoop(CompositeMetadata)

        assertEquals(5, decoded.entries.size)

        decoded.list(WellKnownMimeType.ApplicationAvro).let {
            assertEquals(0, it.size)
        }

        decoded.list(CustomMimeType("custom2")).let {
            assertEquals(0, it.size)
        }

        decoded.list(ReservedMimeType(110)).let {
            assertEquals(0, it.size)
        }

        decoded.list(WellKnownMimeType.MessageRSocketRouting).let {
            assertEquals(1, it.size)
            assertEquals("routing metadata", it[0].readText())
        }

        decoded.list(CustomMimeType("custom")).let {
            assertEquals(2, it.size)
            assertEquals("custom metadata - 1", it[0].readText())
            assertEquals("custom metadata - 2", it[1].readText())
        }

        decoded.list(ReservedMimeType(120)).let {
            assertEquals(2, it.size)
            assertEquals("reserved metadata - 1", it[0].readText())
            assertEquals("reserved metadata - 2", it[1].readText())
        }
    }

    @Test
    fun testBuilderReleaseOnError() {
        val packet = packet("string")
        assertFails {
            buildCompositeMetadata {
                add(WellKnownMimeType.ApplicationAvro, packet)
                error("")
            }
        }
        assertTrue(packet.isEmpty)
    }

    @Test
    fun testCombine() {
        val cm = buildCompositeMetadata {
            add(RoutingMetadata("tag1", "tag2"))
            add(
                PerStreamAcceptableDataMimeTypesMetadata(
                    WellKnownMimeType.ApplicationAvro,
                    CustomMimeType("application/custom"),
                    ReservedMimeType(120)
                )
            )
            add(WellKnownMimeType.ApplicationJson, packet("{}"))
        }

        val decoded = cm.readLoop(CompositeMetadata)
        assertEquals(3, decoded.entries.size)

        assertEquals(listOf("tag1", "tag2"), decoded[RoutingMetadata].tags)
        assertEquals(
            listOf(
                WellKnownMimeType.ApplicationAvro,
                CustomMimeType("application/custom"),
                ReservedMimeType(120)
            ),
            decoded[PerStreamAcceptableDataMimeTypesMetadata].types
        )
        assertEquals("{}", decoded[WellKnownMimeType.ApplicationJson].readText())
    }

    @Test
    fun failOnNonAscii() {
        assertFailsWith(IllegalArgumentException::class) {
            CustomMimeType("1234567#4? 𠜎𠜱𠝹𠱓𠱸𠲖𠳏𠳕𠴕𠵼𠵿𠸎")
        }
    }

}
