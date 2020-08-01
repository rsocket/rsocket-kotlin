package io.rsocket.server

import io.rsocket.flow.*
import io.rsocket.frame.*
import io.rsocket.plugin.*

data class RSocketServerConfiguration(
    val plugin: Plugin = Plugin(),
    val fragmentation: Int = 0,
    val requestStrategy: () -> RequestStrategy = RequestStrategy.Default,
    val ignoredFrameConsumer: (Frame) -> Unit = {}
)
