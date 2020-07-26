package dev.whyoleg.rsocket.server

import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.frame.*
import dev.whyoleg.rsocket.plugin.*

data class RSocketServerConfiguration(
    val plugin: Plugin = Plugin(),
    val fragmentation: Int = 0,
    val requestStrategy: () -> RequestStrategy = RequestStrategy.Default,
    val ignoredFrameConsumer: (Frame) -> Unit = {}
)
