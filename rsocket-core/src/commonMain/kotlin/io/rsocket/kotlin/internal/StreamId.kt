/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.internal

import kotlinx.atomicfu.*

internal class StreamId(streamId: Int) {
    private val streamId = atomic(streamId)

    fun next(streamIds: IntMap<*>): Int {
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

        operator fun invoke(isServer: Boolean): StreamId = if (isServer) server() else client()
    }
}
