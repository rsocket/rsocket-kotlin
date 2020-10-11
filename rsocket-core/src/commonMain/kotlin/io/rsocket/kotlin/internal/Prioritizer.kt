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

import io.rsocket.kotlin.frame.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*

internal class Prioritizer {
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
        priorityChannel.closeReceivedElements()
        commonChannel.closeReceivedElements()
        priorityChannel.close(throwable)
        commonChannel.close(throwable)
    }
}
