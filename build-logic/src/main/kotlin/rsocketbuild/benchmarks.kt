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

package rsocketbuild

import kotlinx.benchmark.gradle.*
import org.gradle.kotlin.dsl.support.*

fun BenchmarksExtension.registerBenchmarks(
    implName: String,
    transports: List<String>,
    customize: BenchmarkConfiguration.(transport: String) -> Unit = {},
) {
    fun register(
        operation: String,
        transport: String,
        format: String,
        kind: String,
    ) {
        configurations.register(
            listOf(kind, format, transport, operation).joinToString("", transform = String::uppercaseFirstChar)
        ) {
            reportFormat = format
            when (kind) {
                "fast" -> param("payloadSize", "0")
                "full" -> param("payloadSize", "0", "64", "4096")
                else   -> error("wrong `kind`=$kind")
            }

            include("${transport.uppercaseFirstChar()}${implName}Benchmark.${operation}Blocking")
            include("${transport.uppercaseFirstChar()}${implName}Benchmark.${operation}Parallel")
            include("${transport.uppercaseFirstChar()}${implName}Benchmark.${operation}Concurrent")

            customize(transport)
        }
    }

    val operations = listOf("requestResponse", "requestStream", "requestChannel")
    val formats = listOf("text", "csv", "json")
    val kinds = listOf("fast", "full")
    transports.forEach { transport ->
        operations.forEach { operation ->
            formats.forEach { format ->
                kinds.forEach { kind ->
                    register(operation, transport, format, kind)
                }
            }
        }
    }
}

//if (transport == "local") {
//    param("dispatcher", "DEFAULT", "UNCONFINED")
////                        param("channels", "S", "M")
//}