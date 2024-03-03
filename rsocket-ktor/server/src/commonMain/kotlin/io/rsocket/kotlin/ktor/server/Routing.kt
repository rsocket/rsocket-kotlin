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

package io.rsocket.kotlin.ktor.server

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*

@OptIn(RSocketTransportApi::class)
public fun Route.rSocket(protocol: String? = null, acceptor: ConnectionAcceptor): Unit =
    webSocket(protocol, application.plugin(RSocketSupport).handler(acceptor))

@OptIn(RSocketTransportApi::class)
public fun Route.rSocket(path: String, protocol: String? = null, acceptor: ConnectionAcceptor): Unit =
    webSocket(path, protocol, application.plugin(RSocketSupport).handler(acceptor))
