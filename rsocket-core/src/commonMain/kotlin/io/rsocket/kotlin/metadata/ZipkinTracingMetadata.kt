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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*
import kotlinx.io.*
import kotlin.experimental.*

@ExperimentalMetadataApi
public class ZipkinTracingMetadata internal constructor(
    public val kind: Kind,
    public val hasIds: Boolean, //trace and span ids
    public val hasParentSpanId: Boolean,
    public val extendedTraceId: Boolean,
    public val traceId: Long,
    public val traceIdHigh: Long,
    public val spanId: Long,
    public val parentSpanId: Long,
) : Metadata {
    override val mimeType: MimeType get() = Reader.mimeType

    public enum class Kind {
        Debug,
        Sample,
        NotSampled,
        Unspecified
    }

    override fun Sink.writeSelf() {
        var flags = when (kind) {
            Kind.Debug       -> DebugFlag
            Kind.Sample      -> SampleFlag
            Kind.NotSampled  -> NotSampledFlag
            Kind.Unspecified -> 0
        }

        if (!hasIds) return writeByte(flags)

        flags = flags or HasIdsFlag

        if (extendedTraceId) flags = flags or ExtendedTraceFlag
        if (hasParentSpanId) flags = flags or HasParentSpanFlag

        writeByte(flags)

        if (extendedTraceId) writeLong(traceIdHigh)
        writeLong(traceId)
        writeLong(spanId)
        if (hasParentSpanId) writeLong(parentSpanId)
    }

    override fun close(): Unit = Unit

    public companion object Reader : MetadataReader<ZipkinTracingMetadata> {
        override val mimeType: MimeType get() = WellKnownMimeType.MessageRSocketTracingZipkin
        override fun Source.read(): ZipkinTracingMetadata {
            val flags = readByte()

            val kind = when {
                flags check DebugFlag      -> Kind.Debug
                flags check SampleFlag     -> Kind.Sample
                flags check NotSampledFlag -> Kind.NotSampled
                else                       -> Kind.Unspecified
            }

            if (!(flags check HasIdsFlag)) return ZipkinTracingMetadata(kind)

            val extendedTraceId = flags check ExtendedTraceFlag
            val hasParentSpanId = flags check HasParentSpanFlag

            val traceIdHigh = if (extendedTraceId) readLong() else 0
            val traceId = readLong()
            val spanId = readLong()
            val parentSpanId = if (hasParentSpanId) readLong() else 0

            return ZipkinTracingMetadata(
                kind = kind,
                hasIds = true,
                hasParentSpanId = hasParentSpanId,
                extendedTraceId = extendedTraceId,
                traceId = traceId,
                traceIdHigh = traceIdHigh,
                spanId = spanId,
                parentSpanId = parentSpanId
            )
        }
    }
}

@ExperimentalMetadataApi
public fun ZipkinTracingMetadata(kind: ZipkinTracingMetadata.Kind): ZipkinTracingMetadata =
    ZipkinTracingMetadata(kind, false, false, false, 0, 0, 0, 0)

@ExperimentalMetadataApi
public fun ZipkinTracingMetadata(
    kind: ZipkinTracingMetadata.Kind,
    traceId: Long,
    spanId: Long,
): ZipkinTracingMetadata = ZipkinTracingMetadata(kind, true, false, false, traceId, 0, spanId, 0)

@ExperimentalMetadataApi
public fun ZipkinTracingMetadata(
    kind: ZipkinTracingMetadata.Kind,
    traceId: Long,
    spanId: Long,
    parentSpanId: Long,
): ZipkinTracingMetadata = ZipkinTracingMetadata(kind, true, true, false, traceId, 0, spanId, parentSpanId)

@Suppress("FunctionName")
@ExperimentalMetadataApi
public fun ZipkinTracingMetadata128(
    kind: ZipkinTracingMetadata.Kind,
    traceId: Long,
    traceIdHigh: Long,
    spanId: Long,
): ZipkinTracingMetadata = ZipkinTracingMetadata(kind, true, false, true, traceId, traceIdHigh, spanId, 0)

@Suppress("FunctionName")
@ExperimentalMetadataApi
public fun ZipkinTracingMetadata128(
    kind: ZipkinTracingMetadata.Kind,
    traceId: Long,
    traceIdHigh: Long,
    spanId: Long,
    parentSpanId: Long,
): ZipkinTracingMetadata = ZipkinTracingMetadata(kind, true, true, true, traceId, traceIdHigh, spanId, parentSpanId)


private const val HasIdsFlag: Byte = Byte.MIN_VALUE
private const val DebugFlag: Byte = 0b01000000
private const val SampleFlag: Byte = 0b00100000
private const val NotSampledFlag: Byte = 0b00010000
private const val ExtendedTraceFlag: Byte = 0b00001000
private const val HasParentSpanFlag: Byte = 0b00000100
