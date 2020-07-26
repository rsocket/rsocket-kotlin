package dev.whyoleg.rsocket.keepalive

import kotlin.time.*

data class KeepAlive(
    val interval: Duration = 20.seconds,
    val maxLifetime: Duration = 90.seconds
)
