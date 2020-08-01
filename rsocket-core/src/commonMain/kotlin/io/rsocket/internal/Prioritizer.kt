package io.rsocket.internal

import io.rsocket.frame.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*

class Prioritizer {
    private val priorityChannel = Channel<Frame>(Channel.UNLIMITED)
    private val commonChannel = Channel<Frame>(Channel.UNLIMITED)

    fun send(frame: Frame) {
        commonChannel.offer(frame)
    }

    fun sendPrioritized(frame: Frame) {
        priorityChannel.offer(frame)
    }

    suspend fun receive(): Frame {
        priorityChannel.poll()?.let { return it }
        commonChannel.poll()?.let { return it }
        return select {
            priorityChannel.onReceive { it }
            commonChannel.onReceive { it }
        }
    }

    fun close(throwable: Throwable?) {
        priorityChannel.close(throwable)
        commonChannel.close(throwable)
    }
}
