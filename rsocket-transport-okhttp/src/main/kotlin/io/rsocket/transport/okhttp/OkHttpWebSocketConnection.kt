package io.rsocket.transport.okhttp

import io.reactivex.Flowable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import org.reactivestreams.Publisher

class OkHttpWebSocketConnection internal constructor(private val ws: OkWebsocket)
    : DuplexConnection {

    override fun availability(): Double = if (ws.isOpen) 1.0 else 0.0

    override fun close() = ws.close()

    override fun onClose() = ws.onClose()

    override fun send(frame: Publisher<Frame>) = ws.send(frame)

    override fun receive(): Flowable<Frame> = ws.receive()
}