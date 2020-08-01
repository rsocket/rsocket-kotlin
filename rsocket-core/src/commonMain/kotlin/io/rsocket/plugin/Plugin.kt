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

package io.rsocket.plugin

import io.rsocket.*
import io.rsocket.connection.*

data class Plugin(
    val connection: List<ConnectionInterceptor> = emptyList(),
    val requester: List<RSocketInterceptor> = emptyList(),
    val responder: List<RSocketInterceptor> = emptyList(),
    val acceptor: List<RSocketAcceptorInterceptor> = emptyList()
)

operator fun Plugin.plus(other: Plugin): Plugin = Plugin(
    connection = connection + other.connection,
    requester = requester + other.requester,
    responder = responder + other.responder,
    acceptor = acceptor + other.acceptor
)

internal fun Plugin.wrapConnection(connection: Connection): Connection = this.connection.fold(connection) { c, i -> i(c) }
internal fun Plugin.wrapRequester(requester: RSocket): RSocket = this.requester.fold(requester) { r, i -> i(r) }
internal fun Plugin.wrapResponder(responder: RSocket): RSocket = this.responder.fold(responder) { r, i -> i(r) }
internal fun Plugin.wrapAcceptor(acceptor: RSocketAcceptor): RSocketAcceptor = this.acceptor.fold(acceptor) { r, i -> i(r) }
