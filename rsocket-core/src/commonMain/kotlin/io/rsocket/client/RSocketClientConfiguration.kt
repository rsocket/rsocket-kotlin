package io.rsocket.client

import io.rsocket.*
import io.rsocket.flow.*
import io.rsocket.frame.*
import io.rsocket.frame.io.*
import io.rsocket.keepalive.*
import io.rsocket.payload.*
import io.rsocket.plugin.*

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
