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
