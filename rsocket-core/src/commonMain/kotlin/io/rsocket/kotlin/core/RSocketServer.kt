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
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.error.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.plugin.*
import kotlinx.coroutines.*

class RSocketServer(
    private val connectionProvider: ConnectionProvider,
    private val configuration: RSocketServerConfiguration = RSocketServerConfiguration(),
) {
    suspend fun start(acceptor: RSocketAcceptor): Job {
        val connection = connectionProvider.connect().let(configuration.plugin::wrapConnection)
        val setupFrame = connection.receive().toFrame()
        if (setupFrame !is SetupFrame)
            connection.failSetup(RSocketError.Setup.Invalid("Invalid setup frame: ${setupFrame.type}"))
        if (setupFrame.version != Version.Current)
            connection.failSetup(RSocketError.Setup.Invalid("Unsupported version: ${setupFrame.version}"))

        try {
            return connectServer(
                connection = connection,
                plugin = configuration.plugin,
                setupFrame = setupFrame,
                ignoredFrameConsumer = configuration.ignoredFrameConsumer,
                acceptor = acceptor
            )
        } catch (e: Throwable) {
            connection.failSetup(RSocketError.Setup.Rejected(e.message ?: "Rejected by server acceptor"))
        }
    }

    private suspend fun Connection.failSetup(error: RSocketError.Setup): Nothing {
        send(ErrorFrame(0, error).toPacket())
        cancel("Setup failed", error)
        throw error
    }
}
