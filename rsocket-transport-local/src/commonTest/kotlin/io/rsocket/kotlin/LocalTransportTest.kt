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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class LocalTransportTest : TransportTest() {

    private val testJob: Job = Job()

    override suspend fun before() {
        super.before()

        val clientChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)
        val serverChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)

        val clientConnection = LocalConnection("client", serverChannel, clientChannel, testJob)
        val serverConnection = LocalConnection("server", clientChannel, serverChannel, testJob)
        client = coroutineScope {
            launch {
                serverConnection.startServer(SERVER_CONFIG, ACCEPTOR)
            }
            clientConnection.connectClient(CONNECTOR_CONFIG)
        }
    }

    override suspend fun after() {

        super.after()
        testJob.cancelAndJoin()
    }
}
