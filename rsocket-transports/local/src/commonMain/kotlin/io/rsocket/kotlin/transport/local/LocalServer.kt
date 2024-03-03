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

@file:OptIn(TransportApi::class)
@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.local

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.js.*

@JsName("LocalServerTransport2") // for compatibility with new API
public fun LocalServerTransport(): ServerTransport<LocalServer> = ServerTransport { accept ->
    val connections = Channel<Connection>()
    val handlerJob = launch {
        supervisorScope {
            connections.consumeEach { connection ->
                launch { accept(connection) }
            }
        }
    }
    LocalServer(connections, coroutineContext + SupervisorJob(handlerJob))
}

public class LocalServer internal constructor(
    private val connections: Channel<Connection>,
    override val coroutineContext: CoroutineContext,
) : ClientTransport {
    override suspend fun connect(): Connection {
        val clientChannel = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)
        val serverChannel = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)
        val connectionJob = Job(coroutineContext[Job])
        connectionJob.invokeOnCompletion {
            clientChannel.cancelWithCause(it)
            serverChannel.cancelWithCause(it)
        }
        val connectionContext = coroutineContext + connectionJob
        val clientConnection = LocalConnection(
            sender = serverChannel,
            receiver = clientChannel,
            coroutineContext = connectionContext + CoroutineName("rSocket-local-client")
        )
        val serverConnection = LocalConnection(
            sender = clientChannel,
            receiver = serverChannel,
            coroutineContext = connectionContext + CoroutineName("rSocket-local-server")
        )
        connections.send(serverConnection)
        return clientConnection
    }
}
