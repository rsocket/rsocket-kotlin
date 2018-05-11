package io.rsocket.android.internal

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Duration
import io.rsocket.android.Frame
import io.rsocket.android.exceptions.ConnectionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class ClientServiceHandler(private val serviceConnection: DuplexConnection,
                                    private val errorConsumer: (Throwable) -> Unit,
                                    keepAliveInfo: KeepAliveInfo)
    : ServiceConnectionHandler(serviceConnection, errorConsumer) {

    @Volatile
    private var timeLastTickSentMs: Long = 0
    private val missedAckCounter: AtomicInteger = AtomicInteger()
    private var subscription: Disposable? = null

    init {
        val tickPeriod = keepAliveInfo.tickPeriod
        val timeout = keepAliveInfo.ackTimeout
        val missedAcks = keepAliveInfo.missedAcks
        if (Duration.ZERO != tickPeriod) {
            val ackTimeoutMs = timeout.toMillis
            subscription = Flowable.interval(tickPeriod.toMillis, TimeUnit.MILLISECONDS)
                    .doOnSubscribe { _ -> timeLastTickSentMs = System.currentTimeMillis() }
                    .concatMap { _ -> sendKeepAlive(ackTimeoutMs, missedAcks).toFlowable<Long>() }
                    .subscribe({},
                            { t: Throwable ->
                                errorConsumer(t)
                                serviceConnection.close().subscribe({}, errorConsumer)
                            })
        }
        serviceConnection.onClose().subscribe({ cleanup() }, errorConsumer)
    }

    override fun handleKeepAlive(frame: Frame) {
        if (!Frame.Keepalive.hasRespondFlag(frame)) {
            timeLastTickSentMs = System.currentTimeMillis()
        }
    }

    private fun cleanup() {
        subscription?.dispose()
    }

    private fun sendKeepAlive(ackTimeoutMs: Long, missedAcks: Int): Completable {
        return Completable.fromRunnable {
            val now = System.currentTimeMillis()
            if (now - timeLastTickSentMs > ackTimeoutMs) {
                val count = missedAckCounter.incrementAndGet()
                if (count >= missedAcks) {
                    val message = String.format(
                            "Missed %d keep-alive acks with a threshold of %d and a ack timeout of %d ms",
                            count, missedAcks, ackTimeoutMs)
                    throw ConnectionException(message)
                }
            }
            sentFrames.onNext(
                    Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
        }
    }
}

internal data class KeepAliveInfo(
        val tickPeriod: Duration = Duration.ZERO,
        val ackTimeout: Duration = Duration.ZERO,
        val missedAcks: Int = 0)
