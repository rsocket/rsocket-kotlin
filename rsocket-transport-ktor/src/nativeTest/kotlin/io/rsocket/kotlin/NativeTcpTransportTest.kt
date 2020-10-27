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

class NativeTcpTransportTest : TransportTest() {
    private lateinit var server: ServerSocket
    private lateinit var serverJob: Job

    override suspend fun before(): Unit = coroutineScope {
        val address = NetworkAddress("0.0.0.0", port.incrementAndGet())
        server = aSocket(serverSelector).tcp().bind(address)
        val clientSocket = aSocket(clientSelector).tcp().clientTransport(address)
        client = CONNECTOR.connect(clientSocket)
        val serverSocket = server.accept()
        val connection = TcpConnection(serverSocket)
        serverJob = connection.job
        SERVER.bind({ accept -> GlobalScope.launch { accept(connection) } }, ACCEPTOR)
    }

    override suspend fun after() {
        serverJob.cancel()
        client.cancel()
        server.close()
        serverJob.join()
        client.join()
    }

    companion object {
        private val port = atomic(Random.nextInt(20, 90) * 100)
        private val clientSelector = SelectorManager()
        private val serverSelector = SelectorManager()
    }
}
