package dev.whyoleg.rsocket

import dev.whyoleg.rsocket.connection.*

typealias RSocketAcceptor = suspend ConnectionSetup.(sendingRSocket: RSocket) -> RSocket
