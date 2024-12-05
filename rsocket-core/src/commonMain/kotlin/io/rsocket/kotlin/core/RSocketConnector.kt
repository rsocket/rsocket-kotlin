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
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(RSocketTransportApi::class, RSocketLoggingApi::class)
public class RSocketConnector internal constructor(
    loggerFactory: LoggerFactory,
    private val maxFragmentSize: Int,
    private val interceptors: Interceptors,
    private val connectionConfigProvider: () -> ConnectionConfig,
    private val acceptor: ConnectionAcceptor,
    private val reconnectPredicate: ReconnectPredicate?,
) {
    private val connectionLogger = loggerFactory.logger("io.rsocket.kotlin.connection")
    private val frameLogger = loggerFactory.logger("io.rsocket.kotlin.frame")

    @Suppress("DEPRECATION_ERROR")
    @Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API")
    public suspend fun connect(transport: ClientTransport): RSocket = connect(object : RSocketClientTarget {
        override val coroutineContext: CoroutineContext get() = transport.coroutineContext
        override fun connectClient(handler: RSocketConnectionInbound): Job = launch {
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
        val connectionConfig = connectionConfigProvider()
        val connectionConfigPayload = connectionConfig.setupPayload.copy()
        val frameCodec = FrameCodec(maxFragmentSize)

        val connection = transport.connectClient().logging(frameLogger)
        val outbound = ConnectionOutbound(frameCodec, connection)
        val context = connection.coroutineContext


        val requester = interceptors.wrapRequester(
            Requester(context.childContext(), outbound)
        )
        val responder = interceptors.wrapResponder(
            with(interceptors.wrapAcceptor(acceptor)) {
                ConnectionAcceptorContext(connectionConfig, requester).accept()
            }
        )

        // link completing of requester, connection and requestHandler
        requester.coroutineContext.job.invokeOnCompletion {
            connection.coroutineContext.job.cancel("Requester cancelled", it)
        }
        responder.coroutineContext.job.invokeOnCompletion {
            connection.coroutineContext.job.cancel("Responder cancelled", it)
        }
        connection.coroutineContext.job.invokeOnCompletion { cause ->
            // the responder is not linked to `coroutineContext`
            responder.cancel("Connection closed", cause)
        }

        val keepAliveHandler = KeepAliveHandler(connectionConfig.keepAlive, outbound)

        connection.startReceiving(

        )

        outbound.sendSetup(
            version = Version.Current,
            honorLease = false,
            keepAlive = connectionConfig.keepAlive,
            resumeToken = null,
            payloadMimeType = connectionConfig.payloadMimeType,
            // copy needed, as it can be used in acceptor
            payload = connectionConfigPayload
        )

        return requester
    }
}
