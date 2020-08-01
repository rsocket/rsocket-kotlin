package io.rsocket.internal

import kotlinx.atomicfu.*

internal class StreamId(streamId: Int) {
    private val streamId = atomic(streamId)

    fun next(streamIds: Map<Int, *>): Int {
        var streamId: Int
        do {
            streamId = this.streamId.addAndGet(2) and MASK
        } while (streamId == 0 || streamId in streamIds)
        return streamId
    }

    fun isBeforeOrCurrent(streamId: Int): Boolean = this.streamId.value >= streamId && streamId > 0

    companion object {
        private const val MASK = 0x7FFFFFFF
        fun client(): StreamId = StreamId(-1)
        fun server(): StreamId = StreamId(0)
    }
}
