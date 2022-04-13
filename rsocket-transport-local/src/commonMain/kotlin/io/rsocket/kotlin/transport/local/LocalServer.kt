/*
 * Copyright 2015-2022 the original author or authors.
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
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

public fun LocalServerTransport(
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
): ServerTransport<LocalServer> = ServerTransport { accept ->
    val connections = Channel<Connection>()
    val handlerJob = launch {
        supervisorScope {
            connections.consumeEach { connection ->
                launch { accept(connection) }
            }
        }
    }
    LocalServer(pool, connections, coroutineContext + SupervisorJob(handlerJob))
}

public class LocalServer internal constructor(
    private val pool: ObjectPool<ChunkBuffer>,
    private val connections: Channel<Connection>,
    override val coroutineContext: CoroutineContext
) : ClientTransport {
    override suspend fun connect(): Connection {
        val clientChannel = @Suppress("INVISIBLE_MEMBER") SafeChannel<ByteReadPacket>(Channel.UNLIMITED)
        val serverChannel = @Suppress("INVISIBLE_MEMBER") SafeChannel<ByteReadPacket>(Channel.UNLIMITED)
        val connectionJob = Job(coroutineContext[Job])
        connectionJob.invokeOnCompletion {
            @Suppress("INVISIBLE_MEMBER") clientChannel.fullClose(it)
            @Suppress("INVISIBLE_MEMBER") serverChannel.fullClose(it)
        }
        val connectionContext = coroutineContext + connectionJob
        val clientConnection =
            LocalConnection(serverChannel, clientChannel, pool, connectionContext + CoroutineName("rSocket-local-client"))
        val serverConnection =
            LocalConnection(clientChannel, serverChannel, pool, connectionContext + CoroutineName("rSocket-local-server"))
        connections.send(serverConnection)
        return clientConnection
    }
}
