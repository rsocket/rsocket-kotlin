package io.rsocket.keepalive

import io.rsocket.error.*
import io.rsocket.frame.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.time.*
import kotlin.time.TimeSource.*

internal class KeepAliveHandler(
    private val keepAlive: KeepAlive,
    private val offerFrame: (frame: Frame) -> Unit
) {

    private val lastMark = atomic<TimeMark?>(null)

    fun receive(frame: KeepAliveFrame) {
        lastMark.value = Monotonic.markNow()
        if (frame.respond) {
            offerFrame(KeepAliveFrame(false, 0, frame.data))
        }
    }

    fun startIn(scope: CoroutineScope) {
        lastMark.value = Monotonic.markNow()
        scope.launch {
            while (isActive) {
                delay(keepAlive.interval)
                if (lastMark.value!!.elapsedNow() >= keepAlive.maxLifetime) {
                    throw RSocketError.ConnectionError("No keep-alive for ${keepAlive.maxLifetime}")
                }
                offerFrame(KeepAliveFrame(true, 0, byteArrayOf()))
            }
        }
    }
}
