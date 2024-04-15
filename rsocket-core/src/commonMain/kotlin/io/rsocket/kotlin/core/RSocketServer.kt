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
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*

@OptIn(RSocketTransportApi::class, RSocketLoggingApi::class)
public class RSocketServer internal constructor(
    loggerFactory: LoggerFactory,
    private val maxFragmentSize: Int,
    private val interceptors: Interceptors,
    private val bufferPool: BufferPool,
) {
    private val frameLogger = loggerFactory.logger("io.rsocket.kotlin.frame")

    @Suppress("DEPRECATION_ERROR")
    @Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API")
    @DelicateCoroutinesApi
    public fun <T> bind(
        transport: ServerTransport<T>,
        acceptor: ConnectionAcceptor,
    ): T = bindIn(GlobalScope, transport, acceptor)

    @Suppress("DEPRECATION_ERROR")
    @Deprecated(level = DeprecationLevel.ERROR, message = "Deprecated in favor of new Transport API")
    public fun <T> bindIn(
        scope: CoroutineScope,
        transport: ServerTransport<T>,
        acceptor: ConnectionAcceptor,
    ): T {
        val handler = createHandler(acceptor)
        return with(transport) {
            scope.start {
                handler.handleConnection(interceptors.wrapConnection(it))
            }
        }
    }

    public suspend fun <T : RSocketServerInstance> startServer(
        transport: RSocketServerTarget<T>,
        acceptor: ConnectionAcceptor,
    ): T = transport.startServer(createHandler(acceptor))

    @RSocketTransportApi
    public fun createHandler(acceptor: ConnectionAcceptor): RSocketConnectionHandler =
        AcceptConnection(acceptor).logging(frameLogger, bufferPool)

    private inner class AcceptConnection(acceptor: ConnectionAcceptor) : ConnectionEstablishmentHandler(
        isClient = false,
        frameCodec = FrameCodec(bufferPool, maxFragmentSize),
        connectionAcceptor = acceptor,
        interceptors = interceptors,
        requesterDeferred = null
    ) {
        override suspend fun establishConnection(context: ConnectionEstablishmentContext): ConnectionConfig {
            val setupFrame = context.receiveFrame()
            return try {
                when {
                    setupFrame !is SetupFrame             -> throw RSocketError.Setup.Invalid("Invalid setup frame: ${setupFrame.type}")
                    setupFrame.version != Version.Current -> throw RSocketError.Setup.Unsupported("Unsupported version: ${setupFrame.version}")
                    setupFrame.honorLease                 -> throw RSocketError.Setup.Unsupported("Lease is not supported")
                    setupFrame.resumeToken != null        -> throw RSocketError.Setup.Unsupported("Resume is not supported")
                    else                                  -> {
                        ConnectionConfig(
                            keepAlive = setupFrame.keepAlive,
                            payloadMimeType = setupFrame.payloadMimeType,
                            setupPayload = setupFrame.payload
                        )
                    }
                }
            } catch (cause: Throwable) {
                setupFrame.close()
                throw cause
            }
        }
    }
}
