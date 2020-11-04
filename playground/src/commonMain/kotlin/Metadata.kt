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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.metadata.*
import io.rsocket.kotlin.payload.*

@OptIn(ExperimentalMetadataApi::class)
public fun metadata() {

    //writing

    val routing = RoutingMetadata("tag1", "route1", "something")
    val tracing = ZipkinTracingMetadata(
        kind = ZipkinTracingMetadata.Kind.Sample,
        traceId = 123L,
        spanId = 123L
    )
    val cm1 = buildCompositeMetadata {
        add(routing)
        add(tracing)
    }
    // or
    val cm2 = CompositeMetadata(routing, tracing)

    //all lambdas are inline, and don't create another objects for builders
    //so no overhead comparing to using plain Payload(data, metadata) function
    val payload = buildPayload {
        data("Some data")
        metadata(cm2)
        // or
        metadata(cm1)
        // or
        compositeMetadata {
            add(routing)
            add(tracing)

            add(WellKnownMimeType.ApplicationAvro, buildPacket { writeText("some custom metadata building") })
            //or
            val raw = RawMetadata(WellKnownMimeType.ApplicationAvro, ByteReadPacket.Empty)

            add(raw)
        }
    }

    //reading
    //property types are not needed, just for better online readability
    val cm: CompositeMetadata = payload.metadata?.read(CompositeMetadata) ?: return

    val rmr = RawMetadata.reader(WellKnownMimeType.ApplicationAvro)


    val rm: RoutingMetadata = cm.get(RoutingMetadata)
    val tm: ZipkinTracingMetadata = cm.get(ZipkinTracingMetadata)

    //or
    val rm1: RoutingMetadata = cm[RoutingMetadata]
    val tm1: ZipkinTracingMetadata = cm[ZipkinTracingMetadata]

    //or
    val rmNull: RoutingMetadata? = cm.getOrNull(RoutingMetadata)
    val tmNull: ZipkinTracingMetadata? = cm.getOrNull(ZipkinTracingMetadata)

    //or
    val rmList: List<RoutingMetadata> = cm.list(RoutingMetadata) //spec allow this

    //or
    val c: Boolean = cm.contains(RoutingMetadata)
    val c2: Boolean = RoutingMetadata in cm

    //or

    cm.entries.forEach {
        if (it.hasMimeTypeOf(RoutingMetadata)) {
            val rm2: RoutingMetadata = it.read(RoutingMetadata)
        }
        //or
        val tm2: ZipkinTracingMetadata? = it.readOrNull(ZipkinTracingMetadata)


    }

    //usage

    rm.tags.forEach {
        println("tag: $it")
    }

    tm.traceId
    tm.traceIdHigh

    val span = "${tm.parentSpanId}-${tm.spanId}"

}
