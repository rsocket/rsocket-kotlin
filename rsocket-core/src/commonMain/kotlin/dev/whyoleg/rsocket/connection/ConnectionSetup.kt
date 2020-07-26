package dev.whyoleg.rsocket.connection

import dev.whyoleg.rsocket.frame.*
import dev.whyoleg.rsocket.keepalive.*
import dev.whyoleg.rsocket.payload.*

data class ConnectionSetup(
    val honorLease: Boolean,
    val keepAlive: KeepAlive,
    val payloadMimeType: PayloadMimeType,
    val payload: Payload
)

internal fun SetupFrame.toConnectionSetup(): ConnectionSetup = ConnectionSetup(honorLease, keepAlive, payloadMimeType, payload)
