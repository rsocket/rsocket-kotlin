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

@file:Suppress("FunctionName")

package io.rsocket.kotlin.transport.ktor

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*

public class TcpServer internal constructor(
    public val handlerJob: Job,
    public val serverSocket: Deferred<ServerSocket>
)

public fun TcpServerTransport(
    hostname: String = "0.0.0.0", port: Int = 0,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool,
    configure: SocketOptions.AcceptorOptions.() -> Unit = {},
): ServerTransport<TcpServer> = TcpServerTransport(NetworkAddress(hostname, port), pool, configure)

public fun TcpServerTransport(
    localAddress: NetworkAddress? = null,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool,
    configure: SocketOptions.AcceptorOptions.() -> Unit = {},
): ServerTransport<TcpServer> = ServerTransport { accept ->
    val serverSocketDeferred = CompletableDeferred<ServerSocket>()
    val handlerJob = launch(defaultDispatcher + coroutineContext) {
        @OptIn(InternalAPI::class) SelectorManager(coroutineContext).use { selector ->
            aSocket(selector).tcp().bind(localAddress, configure).use { serverSocket ->
                serverSocketDeferred.complete(serverSocket)
                val connectionScope =
                    CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]) + CoroutineName("rSocket-tcp-server"))
                while (isActive) {
                    val clientSocket = serverSocket.accept()
                    connectionScope.launch {
                        accept(TcpConnection(clientSocket, coroutineContext, pool))
                    }.invokeOnCompletion {
                        clientSocket.close()
                    }
                }
            }
        }
    }
    handlerJob.invokeOnCompletion { it?.let(serverSocketDeferred::completeExceptionally) }
    TcpServer(handlerJob, serverSocketDeferred)
}

