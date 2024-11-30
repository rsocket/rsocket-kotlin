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

package io.rsocket.kotlin.metadata

import kotlin.random.*
import kotlin.test.*

class ZipkinTracingMetadataTest {

    @Test
    fun encodeEmptyTrace() = forAllKinds { kind ->
        val metadata = ZipkinTracingMetadata(kind)

        val decoded = metadata.readLoop(ZipkinTracingMetadata)
        assertEquals(kind, decoded.kind)
        assertFalse(decoded.hasIds)
        assertFalse(decoded.hasParentSpanId)
        assertFalse(decoded.extendedTraceId)
        assertEquals(0, decoded.traceId)
        assertEquals(0, decoded.traceIdHigh)
        assertEquals(0, decoded.spanId)
        assertEquals(0, decoded.parentSpanId)
    }

    @Test
    fun encodeTraceWithTraceAndSpan() = forAllKinds { kind ->
        val traceId = Random.nextLong()
        val spanId = Random.nextLong()
        val metadata = ZipkinTracingMetadata(kind, traceId, spanId)

        val decoded = metadata.readLoop(ZipkinTracingMetadata)
        assertEquals(kind, decoded.kind)
        assertTrue(decoded.hasIds)
        assertFalse(decoded.hasParentSpanId)
        assertFalse(decoded.extendedTraceId)
        assertEquals(traceId, decoded.traceId)
        assertEquals(0, decoded.traceIdHigh)
        assertEquals(spanId, decoded.spanId)
        assertEquals(0, decoded.parentSpanId)
    }

    @Test
    fun encodeTraceWithTraceAndParentSpan() = forAllKinds { kind ->
        val traceId = Random.nextLong()
        val spanId = Random.nextLong()
        val parentSpanId = Random.nextLong()
        val metadata = ZipkinTracingMetadata(kind, traceId, spanId, parentSpanId)

        val decoded = metadata.readLoop(ZipkinTracingMetadata)
        assertEquals(kind, decoded.kind)
        assertTrue(decoded.hasIds)
        assertTrue(decoded.hasParentSpanId)
        assertFalse(decoded.extendedTraceId)
        assertEquals(traceId, decoded.traceId)
        assertEquals(0, decoded.traceIdHigh)
        assertEquals(spanId, decoded.spanId)
        assertEquals(parentSpanId, decoded.parentSpanId)
    }

    @Test
    fun encodeTraceWithExtendedTraceAndSpan() = forAllKinds { kind ->
        val traceId = Random.nextLong()
        val traceIdHigh = Random.nextLong()
        val spanId = Random.nextLong()
        val metadata = ZipkinTracingMetadata128(kind, traceId, traceIdHigh, spanId)

        val decoded = metadata.readLoop(ZipkinTracingMetadata)
        assertEquals(kind, decoded.kind)
        assertTrue(decoded.hasIds)
        assertFalse(decoded.hasParentSpanId)
        assertTrue(decoded.extendedTraceId)
        assertEquals(traceId, decoded.traceId)
        assertEquals(traceIdHigh, decoded.traceIdHigh)
        assertEquals(spanId, decoded.spanId)
        assertEquals(0, decoded.parentSpanId)
    }

    @Test
    fun encodeTraceWithExtendedTraceAndParentSpan() = forAllKinds { kind ->
        val traceId = Random.nextLong()
        val traceIdHigh = Random.nextLong()
        val spanId = Random.nextLong()
        val parentSpanId = Random.nextLong()
        val metadata = ZipkinTracingMetadata128(kind, traceId, traceIdHigh, spanId, parentSpanId)

        val decoded = metadata.readLoop(ZipkinTracingMetadata)
        assertEquals(kind, decoded.kind)
        assertTrue(decoded.hasIds)
        assertTrue(decoded.hasParentSpanId)
        assertTrue(decoded.extendedTraceId)
        assertEquals(traceId, decoded.traceId)
        assertEquals(traceIdHigh, decoded.traceIdHigh)
        assertEquals(spanId, decoded.spanId)
        assertEquals(parentSpanId, decoded.parentSpanId)
    }

    private fun forAllKinds(block: (kind: ZipkinTracingMetadata.Kind) -> Unit) {
        ZipkinTracingMetadata.Kind.values().forEach(block)
    }
}
