package io.rsocket.plugin

import io.rsocket.*
import io.rsocket.connection.*

typealias ConnectionInterceptor = (Connection) -> Connection

typealias RSocketInterceptor = (RSocket) -> RSocket

typealias RSocketAcceptorInterceptor = (RSocketAcceptor) -> RSocketAcceptor
