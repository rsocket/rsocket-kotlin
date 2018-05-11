package io.rsocket.kotlin.internal

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.exceptions.ConnectionException
import io.rsocket.kotlin.KeepAlive
import java.util.concurrent.TimeUnit

internal class ServerServiceHandler(serviceConnection: DuplexConnection,
                                    keepAlive: KeepAlive,
                                    errorConsumer: (Throwable) -> Unit)
    : ServiceHandler(serviceConnection, errorConsumer) {

    @Volatile
    private var keepAliveReceivedMillis = System.currentTimeMillis()
    private var subscription: Disposable? = null

    init {
        val tickPeriod = keepAlive.keepAliveInterval().millis
        val timeout = keepAlive.keepAliveMaxLifeTime().millis
        Flowable.interval(tickPeriod, TimeUnit.MILLISECONDS)
                .concatMapCompletable { checkKeepAlive(timeout) }
                .subscribe({},
                        { err ->
                            errorConsumer(err)
                            serviceConnection.close().subscribe({}, errorConsumer)
                        })

        serviceConnection.onClose().subscribe({ cleanup() }, errorConsumer)
    }

    override fun handleKeepAlive(frame: Frame) {
        if (Frame.Keepalive.hasRespondFlag(frame)) {
            keepAliveReceivedMillis = System.currentTimeMillis()
            val data = Unpooled.wrappedBuffer(frame.data)
            sentFrames.onNext(Frame.Keepalive.from(data, false))
        }
    }

    private fun cleanup() {
        subscription?.dispose()
    }

    private fun checkKeepAlive(timeout: Long): Completable {
        return Completable.fromRunnable {
            val now = System.currentTimeMillis()
            val duration = now - keepAliveReceivedMillis
            if (duration > timeout) {
                val message = String.format(
                        "keep-alive timed out: %d of %d ms", duration, timeout)
                throw ConnectionException(message)
            }
        }
    }
}