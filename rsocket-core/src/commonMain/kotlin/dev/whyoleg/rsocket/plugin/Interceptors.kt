package dev.whyoleg.rsocket.plugin

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.connection.*

typealias ConnectionInterceptor = (Connection) -> Connection

typealias RSocketInterceptor = (RSocket) -> RSocket

typealias RSocketAcceptorInterceptor = (RSocketAcceptor) -> RSocketAcceptor
