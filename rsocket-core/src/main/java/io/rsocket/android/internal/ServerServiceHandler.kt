package io.rsocket.android.internal

import io.netty.buffer.Unpooled
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame

internal class ServerServiceHandler(serviceConnection: DuplexConnection,
                                    errorConsumer: (Throwable) -> Unit)
    : ServiceConnectionHandler(serviceConnection, errorConsumer) {

    override fun handleKeepAlive(frame: Frame) {
        if (Frame.Keepalive.hasRespondFlag(frame)) {
            val data = Unpooled.wrappedBuffer(frame.data)
            sentFrames.onNext(Frame.Keepalive.from(data, false))
        }
    }
}