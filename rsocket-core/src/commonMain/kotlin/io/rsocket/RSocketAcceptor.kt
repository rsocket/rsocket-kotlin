package io.rsocket

import io.rsocket.connection.*

typealias RSocketAcceptor = suspend ConnectionSetup.(sendingRSocket: RSocket) -> RSocket
