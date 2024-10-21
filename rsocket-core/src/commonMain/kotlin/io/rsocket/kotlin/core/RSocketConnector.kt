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

package io.rsocket.kotlin.core

import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(TransportApi::class, RSocketTransportApi::class, RSocketLoggingApi::class)
public class RSocketConnector internal constructor(
    loggerFactory: LoggerFactory,
    private val maxFragmentSize: Int,
    private val interceptors: Interceptors,
    private val connectionConfigProvider: () -> ConnectionConfig,
    private val acceptor: ConnectionAcceptor,
    private val reconnectPredicate: ReconnectPredicate?,
    private val bufferPool: BufferPool,
) {
    private val connectionLogger = loggerFactory.logger("io.rsocket.kotlin.connection")
    private val frameLogger = loggerFactory.logger("io.rsocket.kotlin.frame")

    public suspend fun connect(transport: ClientTransport): RSocket = connect(object : RSocketClientTarget {
        override val coroutineContext: CoroutineContext get() = transport.coroutineContext
        override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
            handler.handleConnection(interceptors.wrapConnection(transport.connect()))
        }
    })

    public suspend fun connect(transport: RSocketClientTarget): RSocket = when (reconnectPredicate) {
        null -> connectOnce(transport)
        else -> connectWithReconnect(
            transport.coroutineContext,
            connectionLogger,
            { connectOnce(transport) },
            reconnectPredicate,
        )
    }

    private suspend fun connectOnce(transport: RSocketClientTarget): RSocket {
        val requesterDeferred = CompletableDeferred<RSocket>()
        val connectJob = transport.connectClient(
            SetupConnection(requesterDeferred).logging(frameLogger, bufferPool)
        ).onCompletion { if (it != null) requesterDeferred.completeExceptionally(it) }
        return try {
            requesterDeferred.await()
        } catch (cause: Throwable) {
            connectJob.cancel("RSocketConnector.connect was cancelled", cause)
            throw cause
        }
    }

    private inner class SetupConnection(requesterDeferred: CompletableDeferred<RSocket>) : ConnectionEstablishmentHandler(
        isClient = true,
        frameCodec = FrameCodec(bufferPool, maxFragmentSize),
        connectionAcceptor = acceptor,
        interceptors = interceptors,
        requesterDeferred = requesterDeferred
    ) {
        override suspend fun establishConnection(context: ConnectionEstablishmentContext): ConnectionConfig {
            val connectionConfig = connectionConfigProvider()
            try {
                context.sendSetup(
                    version = Version.Current,
                    honorLease = false,
                    keepAlive = connectionConfig.keepAlive,
                    resumeToken = null,
                    payloadMimeType = connectionConfig.payloadMimeType,
                    // copy needed, as it can be used in acceptor
                    payload = connectionConfig.setupPayload.copy()
                )
            } catch (cause: Throwable) {
                connectionConfig.setupPayload.close()
                throw cause
            }
            return connectionConfig
        }
    }
}
