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

@file:OptIn(TransportApi::class)
@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

//TODO user should close ClientTransport manually if there is no job provided in context

//this dispatcher will be used, if no dispatcher were provided by user in client and server
internal expect val defaultDispatcher: CoroutineDispatcher

public fun TcpClientTransport(
    hostname: String, port: Int,
    context: CoroutineContext = EmptyCoroutineContext,
    intercept: (Socket) -> Socket = { it }, //f.e. for tls, which is currently supported by ktor only on JVM
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
): ClientTransport = TcpClientTransport(InetSocketAddress(hostname, port), context, intercept, configure)

public fun TcpClientTransport(
    remoteAddress: InetSocketAddress,
    context: CoroutineContext = EmptyCoroutineContext,
    intercept: (Socket) -> Socket = { it }, //f.e. for tls, which is currently supported by ktor only on JVM
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): ClientTransport {
    val transportJob = SupervisorJob(context[Job])
    val transportContext = defaultDispatcher + context + transportJob + CoroutineName("rSocket-tcp-client")
    val selector = SelectorManager(transportContext)
    Job(transportJob).invokeOnCompletion { selector.close() }
    return ClientTransport(transportContext) {
        val socket = aSocket(selector).tcp().connect(remoteAddress, configure)
        TcpConnection(intercept(socket), transportContext + Job(transportJob))
    }
}
