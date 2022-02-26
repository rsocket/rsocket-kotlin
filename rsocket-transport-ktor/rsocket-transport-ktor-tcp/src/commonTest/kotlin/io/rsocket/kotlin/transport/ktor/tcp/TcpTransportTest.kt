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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.sockets.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*

class TcpTransportTest : TransportTest() {
    private val testJob = Job()

    override suspend fun before() {
        val address = InetSocketAddress("0.0.0.0", PortProvider.next())
        val context = testJob + CoroutineExceptionHandler { c, e -> println("$c -> $e") }
        SERVER.bindIn(
            CoroutineScope(context),
            TcpServerTransport(address, InUseTrackingPool),
            ACCEPTOR
        ).serverSocket.await()
        client = CONNECTOR.connect(TcpClientTransport(address, context, InUseTrackingPool))
    }

    override suspend fun after() {
        super.after()
        testJob.cancelAndJoin()
    }
}
