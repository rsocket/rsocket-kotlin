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

@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.util.network.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*

@InternalAPI //because of selector
public fun TcpServerTransport(
    selector: SelectorManager,
    hostname: String = "0.0.0.0", port: Int = 0,
    configure: SocketOptions.AcceptorOptions.() -> Unit = {},
): ServerTransport<Job> = TcpServerTransport(selector, NetworkAddress(hostname, port), configure)

@InternalAPI //because of selector
public fun TcpServerTransport(
    selector: SelectorManager,
    localAddress: NetworkAddress? = null,
    configure: SocketOptions.AcceptorOptions.() -> Unit = {},
): ServerTransport<Job> = ServerTransport { accept ->
    val serverSocket = aSocket(selector).tcp().bind(localAddress, configure)
    GlobalScope.launch(serverSocket.socketContext + Dispatchers.Unconfined + ignoreExceptionHandler, CoroutineStart.UNDISPATCHED) {
        supervisorScope {
            while (isActive) {
                val clientSocket = serverSocket.accept()
                val connection = TcpConnection(clientSocket)
                launch(start = CoroutineStart.UNDISPATCHED) { accept(connection) }
            }
        }
    }
    serverSocket.socketContext
}
