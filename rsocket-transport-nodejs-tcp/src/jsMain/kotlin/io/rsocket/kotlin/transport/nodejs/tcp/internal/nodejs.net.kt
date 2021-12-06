@file:JsModule("net")
@file:JsNonModule

package io.rsocket.kotlin.transport.nodejs.tcp.internal

import org.khronos.webgl.*

internal external fun connect(port: Int, host: String): Socket
internal external fun createServer(connectionListener: (socket: Socket) -> Unit): Server

internal external interface Server {
    fun listen(port: Int, hostname: String)
    fun close(callback: (err: Error) -> Unit)
    fun on(event: String /* "close" */, listener: () -> Unit)
}

internal external interface Socket {
    fun write(buffer: Uint8Array): Boolean
    fun destroy(error: Error = definedExternally)
    fun on(event: String /* "close" */, listener: (had_error: Boolean) -> Unit): Socket
    fun on(event: String /* "data" */, listener: (data: Uint8Array) -> Unit): Socket
    fun on(event: String /* "error" */, listener: (err: Error) -> Unit): Socket
}
