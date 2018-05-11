package io.rsocket.android.internal

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.exceptions.ConnectionException
import io.rsocket.android.util.KeepAlive
import java.util.concurrent.TimeUnit

internal class ClientServiceHandler(serviceConnection: DuplexConnection,
                                    keepAlive: KeepAlive,
                                    errorConsumer: (Throwable) -> Unit)
    : ServiceHandler(serviceConnection, errorConsumer) {

    @Volatile
    private var keepAliveReceivedMillis = System.currentTimeMillis()
    private var subscription: Disposable? = null

    init {
        val tickPeriod = keepAlive.keepAliveInterval().millis
        val timeout = keepAlive.keepAliveMaxLifeTime().millis
        subscription = Flowable.interval(tickPeriod, TimeUnit.MILLISECONDS)
                .concatMapCompletable { sendAndCheckKeepAlive(timeout) }
                .subscribe({},
                        { t: Throwable ->
                            errorConsumer(t)
                            serviceConnection.close().subscribe({}, errorConsumer)
                        })
        serviceConnection.onClose().subscribe({ cleanup() }, errorConsumer)
    }

    override fun handleKeepAlive(frame: Frame) {
        if (!Frame.Keepalive.hasRespondFlag(frame)) {
            keepAliveReceivedMillis = System.currentTimeMillis()
        }
    }

    private fun cleanup() {
        subscription?.dispose()
    }

    private fun sendAndCheckKeepAlive(timeout: Long): Completable {
        return Completable.fromRunnable {
            val now = System.currentTimeMillis()
            val duration = now - keepAliveReceivedMillis
            if (duration > timeout) {
                val message =
                        "keep-alive timed out: $duration of $timeout ms"
                throw ConnectionException(message)
            }
            sentFrames.onNext(
                    Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
        }
    }
}
