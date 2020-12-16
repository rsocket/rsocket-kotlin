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

package io.rsocket.kotlin.core

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*

@OptIn(TransportApi::class)
class RSocketServer internal constructor(
    private val loggerFactory: LoggerFactory,
    private val interceptors: Interceptors,
) {

    fun <T> bind(
        transport: ServerTransport<T>,
        block: suspend ConnectionAcceptorContext.() -> RSocket
    ): T = bind(transport, ConnectionAcceptor(block))

    fun <T> bind(
        transport: ServerTransport<T>,
        acceptor: ConnectionAcceptor,
    ): T = transport.start {
        val connection = it.wrapConnection()
        val setupFrame = connection.validateSetup()
        connection.start(setupFrame, acceptor)
        connection.join()
    }

    private suspend fun Connection.start(setupFrame: SetupFrame, acceptor: ConnectionAcceptor) {
        val connectionConfig = ConnectionConfig(
            keepAlive = setupFrame.keepAlive,
            payloadMimeType = setupFrame.payloadMimeType,
            setupPayload = setupFrame.payload
        )
        try {
            connect(isServer = true, interceptors, connectionConfig, acceptor)
        } catch (e: Throwable) {
            failSetup(RSocketError.Setup.Rejected(e.message ?: "Rejected by server acceptor"))
        }
    }

    private suspend fun Connection.validateSetup(): SetupFrame {
        val setupFrame = receiveFrame()
        return when {
            setupFrame !is SetupFrame             -> failSetup(RSocketError.Setup.Invalid("Invalid setup frame: ${setupFrame.type}"))
            setupFrame.version != Version.Current -> failSetup(RSocketError.Setup.Unsupported("Unsupported version: ${setupFrame.version}"))
            setupFrame.honorLease                 -> failSetup(RSocketError.Setup.Unsupported("Lease is not supported"))
            setupFrame.resumeToken != null        -> failSetup(RSocketError.Setup.Unsupported("Resume is not supported"))
            else                                  -> setupFrame
        }
    }

    private fun Connection.wrapConnection(): Connection =
        interceptors.wrapConnection(this)
            .logging(loggerFactory.logger("io.rsocket.kotlin.frame"))

    private suspend fun Connection.failSetup(error: RSocketError.Setup): Nothing {
        sendFrame(ErrorFrame(0, error))
        job.completeExceptionally(error)
        throw error
    }
}
