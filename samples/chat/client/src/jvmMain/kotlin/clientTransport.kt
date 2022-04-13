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

package io.rsocket.kotlin.samples.chat.client

import io.ktor.client.engine.cio.*
import io.rsocket.kotlin.samples.chat.api.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.tcp.*
import io.rsocket.kotlin.transport.ktor.websocket.client.*

internal actual fun clientTransport(
    type: TransportType,
    host: String,
    port: Int
): ClientTransport = when (type) {
    TransportType.TCP -> TcpClientTransport(host, port)
    TransportType.WS  -> WebSocketClientTransport(CIO, host, port)
}
