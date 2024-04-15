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

@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("DEPRECATION_ERROR")
@Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API, use KtorTcpClientTransport")
public fun TcpClientTransport(
    hostname: String, port: Int,
    context: CoroutineContext = EmptyCoroutineContext,
    intercept: (Socket) -> Socket = { it }, //f.e. for tls, which is currently supported by ktor only on JVM
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
): ClientTransport = TcpClientTransport(InetSocketAddress(hostname, port), context, intercept, configure)

@Suppress("DEPRECATION_ERROR")
@Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API, use KtorTcpClientTransport")
public fun TcpClientTransport(
    remoteAddress: InetSocketAddress,
    context: CoroutineContext = EmptyCoroutineContext,
    intercept: (Socket) -> Socket = { it }, //f.e. for tls, which is currently supported by ktor only on JVM
    configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {},
): ClientTransport {
    val transportJob = SupervisorJob(context[Job])
    val transportContext = Dispatchers.IO + context + transportJob + CoroutineName("rSocket-tcp-client")
    val selector = SelectorManager(transportContext)
    Job(transportJob).invokeOnCompletion { selector.close() }
    return ClientTransport(transportContext) {
        val socket = aSocket(selector).tcp().connect(remoteAddress, configure)
        TcpConnection(intercept(socket), transportContext + Job(transportJob))
    }
}
