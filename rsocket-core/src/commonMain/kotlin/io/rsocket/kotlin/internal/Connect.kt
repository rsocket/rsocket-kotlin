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
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.plugin.*
import kotlinx.coroutines.*

private suspend inline fun connect(
    isServer: Boolean,
    connection: Connection,
    plugin: Plugin,
    setupFrame: SetupFrame,
    noinline acceptor: RSocketAcceptor,
    crossinline beforeStart: suspend () -> Unit,
): Pair<RSocket, Job> {
    val connectionSetup = ConnectionSetup(
        honorLease = setupFrame.honorLease,
        keepAlive = setupFrame.keepAlive,
        payloadMimeType = setupFrame.payloadMimeType,
        payload = setupFrame.payload
    )
    val state = RSocketState(connection, connectionSetup.keepAlive)
    val requester = RSocketRequester(state, StreamId(isServer)).let(plugin::wrapRequester)
    val wrappedAcceptor = acceptor.let(plugin::wrapAcceptor)
    val requestHandler = wrappedAcceptor(connectionSetup, requester).let(plugin::wrapResponder)
    beforeStart()
    return requester to state.start(requestHandler)
}

internal suspend fun connectClient(
    connection: Connection,
    plugin: Plugin,
    setupFrame: SetupFrame,
    acceptor: RSocketAcceptor,
): RSocket = connect(isServer = false, connection, plugin, setupFrame, acceptor) {
    connection.sendFrame(setupFrame)
}.first

internal suspend fun connectServer(
    connection: Connection,
    plugin: Plugin,
    setupFrame: SetupFrame,
    acceptor: RSocketAcceptor,
): Job = connect(isServer = true, connection, plugin, setupFrame, acceptor) {
}.second
