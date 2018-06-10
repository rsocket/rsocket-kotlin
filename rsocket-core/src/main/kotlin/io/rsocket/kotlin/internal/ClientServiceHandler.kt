package io.rsocket.kotlin.internal

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.KeepAlive
import io.rsocket.kotlin.KeepAliveData
import io.rsocket.kotlin.exceptions.ConnectionException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal class ClientServiceHandler(serviceConnection: DuplexConnection,
                                    keepAlive: KeepAlive,
                                    keepAliveData: KeepAliveData,
                                    errorConsumer: (Throwable) -> Unit)
    : ServiceHandler(serviceConnection, errorConsumer) {

    @Volatile
    private var keepAliveReceivedMillis = System.currentTimeMillis()
    private var subscription: Disposable? = null
    private val dataProducer: () -> ByteBuffer = keepAliveData.producer()
    private val dataHandler: (ByteBuffer) -> Unit = keepAliveData.handler()

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
            dataHandler(frame.data)
        } else {
            sendKeepAliveFrame(frame.data, false)
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
            sendKeepAliveFrame(dataProducer(), true)
        }
    }

    private fun sendKeepAliveFrame(data: ByteBuffer, respond: Boolean) {
        sentFrames.onNext(
                Frame.Keepalive.from(Unpooled.wrappedBuffer(data), respond))
    }
}
