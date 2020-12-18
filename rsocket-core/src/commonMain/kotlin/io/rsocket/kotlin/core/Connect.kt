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
import io.rsocket.kotlin.internal.*

@OptIn(TransportApi::class)
internal suspend inline fun Connection.connect(
    isServer: Boolean,
    interceptors: Interceptors,
    connectionConfig: ConnectionConfig,
    acceptor: ConnectionAcceptor,
    beforeStart: () -> Unit = {},
): RSocket {
    val state = RSocketState(this, connectionConfig.keepAlive)
    val requester = RSocketRequester(state, StreamId(isServer)).let(interceptors::wrapRequester)
    val connectionContext = ConnectionAcceptorContext(connectionConfig, requester)
    val requestHandler = with(interceptors.wrapAcceptor(acceptor)) { connectionContext.accept() }.let(interceptors::wrapResponder)
    beforeStart()
    state.start(requestHandler)
    return requester
}
