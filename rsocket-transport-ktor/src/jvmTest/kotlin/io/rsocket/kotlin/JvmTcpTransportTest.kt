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

package io.rsocket.kotlin

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.rsocket.kotlin.test.*
import io.rsocket.kotlin.transport.ktor.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.random.*

class JvmTcpTransportTest : TransportTest() {
    private val selector = SelectorManager(Dispatchers.IO)
    private lateinit var server: TcpServer

    override suspend fun before(): Unit = coroutineScope {
        val address = NetworkAddress("0.0.0.0", port.incrementAndGet())
        val tcp = aSocket(selector).tcp()
        server = tcp.serverTransport(address)
        client = CONNECTOR.connect(tcp.clientTransport(address))
        SERVER.bind(server, ACCEPTOR)
    }

    override suspend fun after() {
        server.socket.socketContext.cancelChildren()
        client.cancelAndJoin()
        server.socket.close()
        server.socket.awaitClosed()
    }

    companion object {
        private val port = atomic(Random.nextInt(20, 90) * 100)
    }
}
