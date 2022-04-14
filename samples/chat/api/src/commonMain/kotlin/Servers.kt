/*
 * Copyright 2015-2022 the original author or authors.
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
    object JVM {
        val TCP = ServerAddress(port = 8001, type = TransportType.TCP)
        val WS = ServerAddress(port = 8002, type = TransportType.WS)
    }

    object JS {
        val TCP = ServerAddress(port = 7001, type = TransportType.TCP)
    }

    object Native {
        val TCP = ServerAddress(port = 9001, type = TransportType.TCP)
        val WS = ServerAddress(port = 9002, type = TransportType.WS)
    }

    val WS = setOf(
        JVM.WS,
        Native.WS
    )
    val TCP = setOf(
        JVM.TCP,
        JS.TCP,
        Native.TCP
    )

    val ALL = WS + TCP
}
