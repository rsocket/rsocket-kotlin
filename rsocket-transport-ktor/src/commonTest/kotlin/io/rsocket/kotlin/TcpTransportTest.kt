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
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.test.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.random.*

abstract class TcpTransportTest : TransportTest() {
    abstract val clientSelector: SelectorManager
    abstract val serverSelector: SelectorManager

    private lateinit var server: ServerSocket
    private lateinit var serverJob: Job

    override suspend fun before(): Unit = coroutineScope {
        val tempServer = aSocket(serverSelector).tcp().bind("0.0.0.0", port.incrementAndGet())
        val serverDef = async { tempServer.accept() }
        val clientSocket = aSocket(clientSelector).tcp().connect(tempServer.localAddress)
        val serverSocket = serverDef.await()
        val serverJobDef = async { serverSocket.connection.startServer(SERVER_CONFIG, ACCEPTOR) }
        client = clientSocket.connection.connectClient(CONNECTOR_CONFIG)
        serverJob = serverJobDef.await()
        server = tempServer
    }

    override suspend fun after() {
        serverJob.cancel()
        client.cancel()
        server.close()
        serverJob.join()
        client.job.join()
    }

    companion object {
        private val port = atomic(Random.nextInt(20, 90) * 100)
    }
}
