package dev.whyoleg.rsocket.client

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.frame.*
import dev.whyoleg.rsocket.frame.io.*
import dev.whyoleg.rsocket.keepalive.*
import dev.whyoleg.rsocket.payload.*
import dev.whyoleg.rsocket.plugin.*

data class RSocketClientConfiguration(
    val plugin: Plugin = Plugin(),
    val fragmentation: Int = 0,
    val keepAlive: KeepAlive = KeepAlive(),
    val payloadMimeType: PayloadMimeType = PayloadMimeType(),
    val setupPayload: Payload = Payload.Empty,
    val requestStrategy: () -> RequestStrategy = RequestStrategy.Default,
    val acceptor: RSocketAcceptor = { RSocketRequestHandler { } },
    val ignoredFrameConsumer: (Frame) -> Unit = {}
)

fun RSocketClientConfiguration.setupFrame(): SetupFrame = SetupFrame(
    version = Version.Current,
    honorLease = false,
    keepAlive = keepAlive,
    resumeToken = null,
    payloadMimeType = payloadMimeType,
    payload = setupPayload
)
