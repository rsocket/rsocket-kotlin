/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*

@RSocketTransportApi
internal abstract class ConnectionInitializer(
    private val isClient: Boolean,
    private val frameCodec: FrameCodec,
    private val connectionAcceptor: ConnectionAcceptor,
    private val interceptors: Interceptors,
) {
    protected abstract suspend fun establishConnection(context: ConnectionEstablishmentContext): ConnectionConfig

    private suspend fun wrapConnection(
        connection: RSocketConnection,
        requestsScope: CoroutineScope,
    ): ConnectionOutbound = when (connection) {
        is RSocketMultiplexedConnection -> {
            val initialStream = when {
                isClient -> connection.createStream()
                else     -> connection.acceptStream() ?: error("Initial stream should be received")
            }
            initialStream.setSendPriority(0)
            MultiplexedConnection(isClient, frameCodec, connection, initialStream, requestsScope)
        }

        is RSocketSequentialConnection  -> {
            SequentialConnection(isClient, frameCodec, connection, requestsScope)
        }
    }

    private suspend fun initialize(connection: RSocketConnection): RSocket {
        val requestsScope = CoroutineScope(connection.coroutineContext.supervisorContext())
        val outbound = wrapConnection(connection, requestsScope)
        val connectionJob = connection.launch(start = CoroutineStart.ATOMIC) {
            try {
                awaitCancellation()
            } catch (cause: Throwable) {
                if (connection.isActive) {
                    nonCancellable {
                        outbound.sendError(RSocketError.ConnectionError(cause.message ?: "Connection failed"))
                    }
                    connection.cancel("Connection failed", cause)
                }
                throw cause
            }
        }
        val connectionScope = CoroutineScope(connection.coroutineContext + connectionJob)
        try {
            val connectionConfig = establishConnection(outbound)
            try {
                val requester = interceptors.wrapRequester(
                    RequesterRSocket(requestsScope, outbound)
                )
                val responder = interceptors.wrapResponder(
                    with(interceptors.wrapAcceptor(connectionAcceptor)) {
                        ConnectionAcceptorContext(connectionConfig, requester).accept()
                    }
                )

                // link completing of requester, connection and requestHandler
                requester.coroutineContext.job.invokeOnCompletion {
                    connectionJob.cancel("Requester cancelled", it)
                }
                responder.coroutineContext.job.invokeOnCompletion {
                    connectionJob.cancel("Responder cancelled", it)
                }
                connectionJob.invokeOnCompletion { cause ->
                    // the responder is not linked to `coroutineContext`
                    responder.cancel("Connection closed", cause)
                }

                val keepAliveHandler = KeepAliveHandler(connectionConfig.keepAlive, outbound, connectionScope)
                connectionScope.launch {
                    outbound.handleConnection(ConnectionInbound(requestsScope, responder, keepAliveHandler))
                }
                return requester
            } catch (cause: Throwable) {
                connectionConfig.setupPayload.close()
                throw cause
            }
        } catch (cause: Throwable) {
            nonCancellable {
                outbound.sendError(
                    when (cause) {
                        is RSocketError -> cause
                        else            -> RSocketError.ConnectionError(cause.message ?: "Connection establishment failed")
                    }
                )
            }
            throw cause
        }
    }

    private fun asyncInitializer(connection: RSocketConnection): Deferred<RSocket> = connection.async {
        try {
            initialize(connection)
        } catch (cause: Throwable) {
            connection.cancel("Connection initialization failed", cause)
            throw cause
        }
    }

    suspend fun runInitializer(connection: RSocketConnection): RSocket {
        val result = asyncInitializer(connection)
        try {
            result.join()
        } catch (cause: Throwable) {
            connection.cancel("Connection initialization cancelled", cause)
            throw cause
        }
        return result.await()
    }

    fun launchInitializer(connection: RSocketConnection): Job = asyncInitializer(connection)
}
