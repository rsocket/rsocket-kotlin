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

package io.rsocket.kotlin.transport.ktor

import io.ktor.network.selector.*
import io.ktor.util.network.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.test.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.random.*

abstract class TcpTransportTest(
    private val clientSelector: SelectorManager,
    private val serverSelector: SelectorManager
) : TransportTest() {
    private lateinit var server: Job

    override suspend fun before() {
        val address = NetworkAddress("0.0.0.0", port.incrementAndGet())
        server = SERVER.bind(TcpServerTransport(serverSelector, address), ACCEPTOR)
        client = CONNECTOR.connect(TcpClientTransport(clientSelector, address))
    }

    override suspend fun after() {
        super.after()
        server.cancelAndJoin()
        clientSelector.close()
        serverSelector.close()
    }

    companion object {
        private val port = atomic(Random.nextInt(20, 90) * 100)
    }
}
