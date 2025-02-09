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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.*

@Suppress("DEPRECATION_ERROR")
class TcpTransportTest : TransportTest() {
    override suspend fun before() {
        val serverSocket = startServer(TcpServerTransport("127.0.0.1")).serverSocket.await()
        client = connectClient(TcpClientTransport(serverSocket.localAddress as InetSocketAddress, testContext))
    }
}

class KtorTcpTransportTest : TransportTest() {
    // a single SelectorManager for both client and server works much better in K/N
    // in user code in most of the cases, only one SelectorManager will be created
    private val selector = SelectorManager(Dispatchers.IoCompatible)
    override suspend fun before() {
        val server = startServer(KtorTcpServerTransport(testContext) {
            selectorManager(selector, false)
        }.target("127.0.0.1"))
        client = connectClient(KtorTcpClientTransport(testContext) {
            selectorManager(selector, false)
        }.target(server.localAddress))
    }

    override suspend fun after() {
        try {
            super.after()
        } finally {
            selector.close()
        }
    }
}
