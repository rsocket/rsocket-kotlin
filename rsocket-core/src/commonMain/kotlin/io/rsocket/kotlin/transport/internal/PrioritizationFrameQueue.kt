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

package io.rsocket.kotlin.transport.internal

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlinx.io.*

private val selectFrame: suspend (ChannelResult<Buffer>) -> ChannelResult<Buffer> = { it }

@RSocketTransportApi
public class PrioritizationFrameQueue(buffersCapacity: Int) {
    private val priorityFrames = bufferChannel(buffersCapacity)
    private val normalFrames = bufferChannel(buffersCapacity)

    private val priorityOnReceive = priorityFrames.onReceiveCatching
    private val normalOnReceive = normalFrames.onReceiveCatching

    // priorityFrames is closed/cancelled first, no need to check `normalFrames`
    @OptIn(DelicateCoroutinesApi::class)
    public val isClosedForSend: Boolean get() = priorityFrames.isClosedForSend

    private fun channel(streamId: Int): SendChannel<Buffer> = when (streamId) {
        0    -> priorityFrames
        else -> normalFrames
    }

    public suspend fun enqueueFrame(streamId: Int, frame: Buffer): Unit = channel(streamId).send(frame)

    public fun tryDequeueFrame(): Buffer? {
        // priority is first
        priorityFrames.tryReceive().onSuccess { return it }
        normalFrames.tryReceive().onSuccess { return it }
        return null
    }

    // TODO: recheck, that it works fine in case priority channel is closed, but normal channel has other frames to send
    public suspend fun dequeueFrame(): Buffer? {
        tryDequeueFrame()?.let { return it }
        return select {
            priorityOnReceive(selectFrame)
            normalOnReceive(selectFrame)
        }.getOrNull()
    }

    // TODO: document
    public fun close() {
        priorityFrames.close()
        normalFrames.close()
    }

    public fun cancel() {
        priorityFrames.cancel()
        normalFrames.cancel()
    }
}
