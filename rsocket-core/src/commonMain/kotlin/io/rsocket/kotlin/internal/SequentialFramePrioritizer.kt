/*
 * Copyright 2015-2024 the original author or authors.
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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.internal.io.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*

private val selectFrame: suspend (ByteReadPacket) -> ByteReadPacket = { it }

internal class SequentialFramePrioritizer {
    private val priorityChannel = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)
    private val commonChannel = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)

    private val priorityOnReceive = priorityChannel.onReceive
    private val commonOnReceive = commonChannel.onReceive

    suspend fun sendPriority(frame: ByteReadPacket): Unit = priorityChannel.send(frame)
    suspend fun sendCommon(frame: ByteReadPacket): Unit = commonChannel.send(frame)

    suspend fun receive(): ByteReadPacket {
        priorityChannel.tryReceive().onSuccess { return it }
        commonChannel.tryReceive().onSuccess { return it }
        return select {
            priorityOnReceive(selectFrame)
            commonOnReceive(selectFrame)
        }
    }

    fun close(error: Throwable?) {
        priorityChannel.cancelWithCause(error)
        commonChannel.cancelWithCause(error)
    }
}
