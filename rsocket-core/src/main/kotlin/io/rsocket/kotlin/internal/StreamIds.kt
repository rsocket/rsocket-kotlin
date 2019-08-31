/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.internal

import java.util.concurrent.atomic.AtomicLongFieldUpdater


internal sealed class StreamIds(streamId: Int) {

    @JvmField
    @Volatile
    internal var streamId: Long = streamId.toLong()

    fun nextStreamId(streamIds: Map<Int, *>): Int {
        var streamId: Int
        do {
            val next = STREAM_ID.addAndGet(this, 2)
            if (next <= MAX_STREAM_ID) {
                return next.toInt()
            }
            streamId = (next and MASK).toInt()
        } while (streamId == 0 || streamIds.containsKey(streamId))
        return streamId
    }

    companion object {
        private val STREAM_ID = AtomicLongFieldUpdater.newUpdater(StreamIds::class.java, "streamId")
        private const val MASK: Long = 0x7FFFFFFF
        internal const val MAX_STREAM_ID = Int.MAX_VALUE


    }
}

internal class ClientStreamIds : StreamIds(-1)

internal class ServerStreamIds : StreamIds(0)

internal class TestStreamIds(streamId: Int) : StreamIds(streamId)
