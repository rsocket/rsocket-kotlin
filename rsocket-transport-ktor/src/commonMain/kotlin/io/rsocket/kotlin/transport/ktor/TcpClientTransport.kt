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

@file:OptIn(TransportApi::class)
@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.util.network.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*

public fun TcpClientTransport(
    selector: SelectorManager,
    hostname: String, port: Int,
    intercept: (Socket) -> Socket = { it }, //f.e. for tls, which is currently supported by ktor only on JVM
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
): ClientTransport = TcpClientTransport(selector, NetworkAddress(hostname, port), intercept, configure)

public fun TcpClientTransport(
    selector: SelectorManager,
    remoteAddress: NetworkAddress,
    intercept: (Socket) -> Socket = { it }, //f.e. for tls, which is currently supported by ktor only on JVM
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
): ClientTransport = ClientTransport {
    val socket = aSocket(selector).tcp().connect(remoteAddress, configure)
    TcpConnection(intercept(socket))
}
