package io.rsocket.connection

import io.rsocket.frame.*
import io.rsocket.keepalive.*
import io.rsocket.payload.*

data class ConnectionSetup(
    val honorLease: Boolean,
    val keepAlive: KeepAlive,
    val payloadMimeType: PayloadMimeType,
    val payload: Payload
)

internal fun SetupFrame.toConnectionSetup(): ConnectionSetup = ConnectionSetup(honorLease, keepAlive, payloadMimeType, payload)
