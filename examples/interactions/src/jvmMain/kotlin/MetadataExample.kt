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
public fun main() {
    val p1 = buildPayload {
        data("Hello")

        metadata("some text")
    }

    println("Case: 1")
    println(p1.data.readText())
    println(p1.metadata?.readText())

    val p2 = buildPayload {
        data("Hello")

        metadata(RoutingMetadata("tag1", "tag2"))
    }

    val tags = p2.metadata?.read(RoutingMetadata)?.tags.orEmpty()
    println("Case: 2")
    println(tags)


    val p3 = buildPayload {
        data("hello")

        compositeMetadata {
            add(RoutingMetadata("tag3", "t4"))
            add(RawMetadata(WellKnownMimeType.ApplicationJson, buildPacket { writeText("{s: 2}") }))
        }
    }

    val cm = p3.metadata!!.read(CompositeMetadata)
    println("Case: 3")
    println(cm[RoutingMetadata].tags)
    println(cm[WellKnownMimeType.ApplicationJson].readText())
}
