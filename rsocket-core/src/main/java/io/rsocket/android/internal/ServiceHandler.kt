package io.rsocket.android.internal

import io.reactivex.processors.UnicastProcessor
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.FrameType
import io.rsocket.android.exceptions.Exceptions

internal abstract class ServiceHandler(private val serviceConnection: DuplexConnection,
                                       private val errorConsumer: (Throwable) -> Unit) {

    internal val sentFrames = UnicastProcessor.create<Frame>()

    init {
        serviceConnection
                .receive()
                .subscribe(::handle, errorConsumer)

        serviceConnection
                .send(sentFrames)
                .subscribe({}, errorConsumer)
    }

    private fun handle(frame: Frame) {
        try {
            when (frame.type) {
                FrameType.LEASE -> handleLease(frame)
                FrameType.ERROR -> handleError(frame)
                FrameType.KEEPALIVE -> handleKeepAlive(frame)
                else -> handleUnknownFrame(frame)
            }
        } finally {
            frame.release()
        }
    }

    protected abstract fun handleKeepAlive(frame: Frame)

    private fun handleLease(frame: Frame) {
        errorConsumer(IllegalArgumentException("Lease is not supported: $frame"))
    }

    private fun handleError(frame: Frame) {
        errorConsumer(Exceptions.from(frame))
        serviceConnection.close().subscribe({}, errorConsumer)

    }

    private fun handleUnknownFrame(frame: Frame) {
        errorConsumer(IllegalArgumentException("Unexpected frame: $frame"))
    }
}