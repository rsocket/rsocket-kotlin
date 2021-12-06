package io.rsocket.kotlin.transport.nodejs.tcp.internal

import org.khronos.webgl.*

internal fun Socket.on(
    onData: (data: Uint8Array) -> Unit,
    onError: (error: Error) -> Unit,
    onClose: (hadError: Boolean) -> Unit
) {
    on("data", onData)
    on("error", onError)
    on("close", onClose)
}

internal fun createServer(
    port: Int,
    hostname: String,
    onClose: () -> Unit,
    listener: (Socket) -> Unit
): Server = createServer(listener).apply {
    on("close", onClose)
    listen(port, hostname)
}
