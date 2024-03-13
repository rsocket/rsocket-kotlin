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

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

@OptIn(RSocketTransportApi::class)
private suspend fun RSocketTransportSession.establishConnection(
    maxFragmentSize: Int,
    bufferPool: BufferPool,
    handler: ConnectionEstablishmentHandler,
): InternalConnection = when (this) {
    is RSocketTransportSession.Sequential -> establishConnection(maxFragmentSize, bufferPool, handler)
    is RSocketTransportSession.Multiplexed -> establishConnection(maxFragmentSize, bufferPool, handler)
}

@OptIn(RSocketTransportApi::class)
internal suspend fun connect(
    session: RSocketTransportSession,
    maxFragmentSize: Int,
    bufferPool: BufferPool,
    handler: ConnectionEstablishmentHandler,
    acceptor: ConnectionAcceptor,
    interceptors: Interceptors,
): RSocket {
    val connection = session.establishConnection(maxFragmentSize, bufferPool, handler)
    val requestsScope = session.supervisorScope()
    val metadataFrames = channelForCloseable<ByteReadPacket>(Channel.BUFFERED)

    return try {
        val requester = interceptors.wrapRequester(
            RequesterRSocket(
                coroutineContext = requestsScope.coroutineContext,
                metadataFrames = metadataFrames,
                executor = RequesterOperationExecutor(
                    requestsScope = requestsScope,
                    operationFactory = connection.requesterOperationFactory
                )
            )
        )
        val responder = interceptors.wrapResponder(
            with(interceptors.wrapAcceptor(acceptor)) {
                ConnectionAcceptorContext(connection.config, requester).accept()
            }
        )

        // link completing of requester, connection and requestHandler
        requester.coroutineContext.job.invokeOnCompletion {
            session.cancel("Requester cancelled", it)
        }
        responder.coroutineContext.job.invokeOnCompletion {
            if (it != null) session.cancel("Request handler failed", it)
        }
        session.coroutineContext.job.invokeOnCompletion {
            requester.cancel("Connection closed", it)
            responder.cancel("Connection closed", it)
            metadataFrames.cancelWithCause(it)
        }

        val keepAliveHandler = KeepAliveHandler(connection.config.keepAlive)
        keepAliveHandler.startIn(session, connection.outbound)

        connection.start(
            ConnectionFrameHandler(ConnectionInboundImpl(session, requestsScope, responder, keepAliveHandler)),
            ResponderWrapper(requestsScope, responder)
        )
        requester
    } catch (cause: Throwable) {
        connection.outbound.sendError(
            when (cause) {
                is RSocketError -> cause
                else            -> RSocketError.ConnectionError(cause.message ?: "Connection failed")
            }
        )
        session.cancel("Connection failed", cause)
        throw cause
    }
}
