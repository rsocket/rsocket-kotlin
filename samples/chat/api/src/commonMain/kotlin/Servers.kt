/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.samples.chat.api

enum class TransportType { TCP, WS }

data class ServerAddress(val port: Int, val type: TransportType)

object Servers {
    val JVM = listOf(
        ServerAddress(port = 9011, type = TransportType.TCP),
        ServerAddress(port = 9012, type = TransportType.WS),
    )

    val JS = listOf(
        ServerAddress(port = 9051, type = TransportType.TCP),
        ServerAddress(port = 9052, type = TransportType.WS),
    )

    val WasmJS = listOf(
        ServerAddress(port = 9061, type = TransportType.TCP),
        ServerAddress(port = 9062, type = TransportType.WS),
    )

    val Native = listOf(
        ServerAddress(port = 9041, type = TransportType.TCP),
        ServerAddress(port = 9042, type = TransportType.WS),
    )
    val ALL = JVM + JS + WasmJS + Native
}
