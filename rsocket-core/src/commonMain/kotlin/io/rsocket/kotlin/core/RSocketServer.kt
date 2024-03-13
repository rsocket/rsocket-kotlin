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

@OptIn(TransportApi::class, RSocketTransportApi::class, RSocketLoggingApi::class)
public class RSocketServer internal constructor(
    loggerFactory: LoggerFactory,
    private val maxFragmentSize: Int,
    private val interceptors: Interceptors,
    private val bufferPool: BufferPool,
) {
    private val frameLogger = loggerFactory.logger("io.rsocket.kotlin.frame")

    @DelicateCoroutinesApi
    public fun <T> bind(
        transport: ServerTransport<T>,
        acceptor: ConnectionAcceptor,
    ): T = bindIn(GlobalScope, transport, acceptor)

    public fun <T> bindIn(
        scope: CoroutineScope,
        transport: ServerTransport<T>,
        acceptor: ConnectionAcceptor,
    ): T {
        val serverAcceptor = createAcceptor(acceptor)
        return with(transport) {
            scope.start {
                val session = interceptors.wrapConnection(it).convert()
                serverAcceptor.acceptSession(session)
                session.coroutineContext.job.join()
            }
        }
    }

    public suspend fun <T : RSocketServerInstance> start(
        transport: RSocketServerTarget<T>,
        acceptor: ConnectionAcceptor,
    ): T = transport.startServer(createAcceptor(acceptor))

    @RSocketTransportApi
    public fun createAcceptor(acceptor: ConnectionAcceptor): RSocketServerAcceptor = Acceptor(acceptor)

    private inner class Acceptor(private val acceptor: ConnectionAcceptor) : RSocketServerAcceptor {
        override suspend fun acceptSession(session: RSocketTransportSession) {
            connect(
                session = session.logging(frameLogger, bufferPool),
                maxFragmentSize = maxFragmentSize,
                bufferPool = bufferPool,
                handler = AcceptConnection,
                acceptor = acceptor,
                interceptors = interceptors
            )
        }
    }
}

private object AcceptConnection : ConnectionEstablishmentHandler {
    override val isClient: Boolean get() = false

    override suspend fun establishConnection(context: ConnectionEstablishmentContext): ConnectionConfig {
        val setupFrame = context.receiveFrame()
        return when {
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
    }
}
