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

package io.rsocket

import io.rsocket.client.*
import io.rsocket.connection.*
import io.rsocket.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

class LocalTest : TransportTest() {
    override suspend fun init(): RSocket {
        val clientChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val serverChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val serverConnection = LocalConnection("server", clientChannel, serverChannel)
        val clientConnection = LocalConnection("client", serverChannel, clientChannel)
        return coroutineScope {
            launch {
                RSocketServer(ConnectionProvider(serverConnection)).start { TestRSocket() }
            }
            RSocketClient(ConnectionProvider(clientConnection)).connect()
        }
    }
}
