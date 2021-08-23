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

package io.rsocket.kotlin.internal

import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.*
import kotlinx.coroutines.*

@OptIn(TransportApi::class)
internal suspend inline fun connect(
    connection: Connection,
    isServer: Boolean,
    maxFragmentSize: Int,
    interceptors: Interceptors,
    connectionConfig: ConnectionConfig,
    acceptor: ConnectionAcceptor
): RSocket {
    val prioritizer = Prioritizer()
    val frameSender = FrameSender(prioritizer, connection.pool, maxFragmentSize)
    val streamsStorage = StreamsStorage(isServer, connection.pool)
    val keepAliveHandler = KeepAliveHandler(connectionConfig.keepAlive, frameSender)

    val requestJob = SupervisorJob(connection.coroutineContext[Job])
    val requestContext = connection.coroutineContext + requestJob

    requestJob.invokeOnCompletion {
        prioritizer.close(it)
        streamsStorage.cleanup(it)
        connectionConfig.setupPayload.close()
    }

    val requester = interceptors.wrapRequester(
        RSocketRequester(requestContext + CoroutineName("rSocket-requester"), frameSender, streamsStorage, connection.pool)
    )
    val requestHandler = interceptors.wrapResponder(
        with(interceptors.wrapAcceptor(acceptor)) {
            ConnectionAcceptorContext(connectionConfig, requester).accept()
        }
    )
    val responder = RSocketResponder(requestContext + CoroutineName("rSocket-responder"), frameSender, requestHandler)

    // link completing of connection and requestHandler
    connection.coroutineContext[Job]?.invokeOnCompletion { requestHandler.cancel("Connection closed", it) }
    requestHandler.coroutineContext[Job]?.invokeOnCompletion { if (it != null) connection.cancel("Request handler failed", it) }

    // start keepalive ticks
    (connection + CoroutineName("rSocket-connection-keep-alive")).launch {
        while (isActive) keepAliveHandler.tick()
    }

    // start sending frames to connection
    (connection + CoroutineName("rSocket-connection-send")).launch {
        while (isActive) connection.sendFrame(prioritizer.receive())
    }

    // start frame handling
    (connection + CoroutineName("rSocket-connection-receive")).launch {
        while (isActive) connection.receiveFrame { frame ->
            when (frame.streamId) {
                0 -> when (frame) {
                    is MetadataPushFrame -> responder.handleMetadataPush(frame.metadata)
                    is ErrorFrame        -> connection.cancel("Error frame received on 0 stream", frame.throwable)
                    is KeepAliveFrame    -> keepAliveHandler.mark(frame)
                    is LeaseFrame        -> frame.close().also { error("lease isn't implemented") }
                    else                 -> frame.close()
                }
                else -> streamsStorage.handleFrame(frame, responder)
            }
        }
    }

    return requester
}
