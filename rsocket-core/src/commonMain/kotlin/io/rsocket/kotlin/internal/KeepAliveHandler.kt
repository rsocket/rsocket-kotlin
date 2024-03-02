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
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.keepalive.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.time.*

internal class KeepAliveHandler(
    private val keepAlive: KeepAlive,
    private val sender: FrameSender,
) {
    private val initial = TimeSource.Monotonic.markNow()
    private fun currentDelayMillis() = initial.elapsedNow().inWholeMilliseconds

    private val lastMark = atomic(currentDelayMillis()) // mark initial timestamp for keepalive

    suspend fun mark(frame: KeepAliveFrame) {
        lastMark.value = currentDelayMillis()
        if (frame.respond) sender.sendKeepAlive(false, 0, frame.data)
    }

    suspend fun tick() {
        delay(keepAlive.intervalMillis.toLong())
        if (currentDelayMillis() - lastMark.value >= keepAlive.maxLifetimeMillis)
            throw RSocketError.ConnectionError("No keep-alive for ${keepAlive.maxLifetimeMillis} ms")

        sender.sendKeepAlive(true, 0, ByteReadPacket.Empty)
    }
}
