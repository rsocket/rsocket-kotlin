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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@RSocketTransportApi
internal abstract class ConnectionEstablishmentHandler(
    private val isClient: Boolean,
    private val frameCodec: FrameCodec,
    private val connectionAcceptor: ConnectionAcceptor,
    private val interceptors: Interceptors,
    private val requesterDeferred: CompletableDeferred<RSocket>?,
) : RSocketConnectionHandler {
    abstract suspend fun establishConnection(context: ConnectionEstablishmentContext): ConnectionConfig

    private suspend fun wrapConnection(
        connection: RSocketConnection,
        requestContext: CoroutineContext,
    ): Connection2 = when (connection) {
        is RSocketMultiplexedConnection -> {
            val initialStream = when {
                isClient -> connection.createStream()
                else     -> connection.acceptStream() ?: error("Initial stream should be received")
            }
            initialStream.setSendPriority(0)
            MultiplexedConnection(isClient, frameCodec, requestContext, connection, initialStream)
        }

        is RSocketSequentialConnection  -> {
            SequentialConnection(isClient, frameCodec, requestContext, connection)
        }
    }

    @Suppress("SuspendFunctionOnCoroutineScope")
    private suspend fun CoroutineScope.handleConnection(connection: Connection2) {
        try {
            val connectionConfig = connection.establishConnection(this@ConnectionEstablishmentHandler)
            try {
                val requester = interceptors.wrapRequester(connection)
                val responder = interceptors.wrapResponder(
                    with(interceptors.wrapAcceptor(connectionAcceptor)) {
                        ConnectionAcceptorContext(connectionConfig, requester).accept()
                    }
                )

                // link completing of requester, connection and requestHandler
                requester.coroutineContext.job.invokeOnCompletion {
                    coroutineContext.job.cancel("Requester cancelled", it)
                }
                responder.coroutineContext.job.invokeOnCompletion {
                    coroutineContext.job.cancel("Responder cancelled", it)
                }
                coroutineContext.job.invokeOnCompletion { cause ->
                    // the responder is not linked to `coroutineContext`
                    responder.cancel("Connection closed", cause)
                }

                requesterDeferred?.complete(requester)

                val keepAliveHandler = KeepAliveHandler(connectionConfig.keepAlive, connection, this)
                connection.handleConnection(
                    ConnectionInbound(connection.coroutineContext, responder, keepAliveHandler)
                )
            } catch (cause: Throwable) {
                connectionConfig.setupPayload.close()
                throw cause
            }
        } catch (cause: Throwable) {
            connection.close()
            withContext(NonCancellable) {
                connection.sendError(
                    when (cause) {
                        is RSocketError -> cause
                        else            -> RSocketError.ConnectionError(cause.message ?: "Connection failed")
                    }
                )
            }
            throw cause
        }
    }

    final override suspend fun handleConnection(connection: RSocketConnection): Unit = coroutineScope {
        handleConnection(wrapConnection(connection, coroutineContext.supervisorContext()))
    }
}
