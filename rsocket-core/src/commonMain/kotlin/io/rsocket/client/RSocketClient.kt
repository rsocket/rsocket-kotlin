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

package io.rsocket.client

import io.rsocket.*
import io.rsocket.connection.*
import io.rsocket.internal.*
import io.rsocket.plugin.*

class RSocketClient(
    private val connectionProvider: ConnectionProvider,
    private val configuration: RSocketClientConfiguration = RSocketClientConfiguration()
) {

    suspend fun connect(): RSocket {
        val connection = connectionProvider.connect().let(configuration.plugin::wrapConnection)
        val setupFrame = configuration.setupFrame()
        val connectionSetup = setupFrame.toConnectionSetup()
        val state = RSocketStateImpl(
            connection = connection,
            keepAlive = configuration.keepAlive,
            requestStrategy = configuration.requestStrategy,
            ignoredFrameConsumer = configuration.ignoredFrameConsumer
        )
        val requester = RSocketRequester(state, StreamId.client()).let(configuration.plugin::wrapRequester)
        val acceptor = configuration.acceptor.let(configuration.plugin::wrapAcceptor)
        val requestHandler = acceptor(connectionSetup, requester).let(configuration.plugin::wrapResponder)
        connection.send(setupFrame.toByteArray())
        state.start(requestHandler)
        return requester
    }
}
