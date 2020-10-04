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

package io.rsocket.kotlin.keepalive

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.error.*
import io.rsocket.kotlin.frame.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.time.*
import kotlin.time.TimeSource.*

@OptIn(ExperimentalTime::class)
internal class KeepAliveHandler(
    private val keepAlive: KeepAlive,
    private val offerFrame: (frame: Frame) -> Unit,
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
                    //for K/N
                    scope.cancel("Keep alive failed", RSocketError.ConnectionError("No keep-alive for ${keepAlive.maxLifetime}"))
                    break
                }
                offerFrame(KeepAliveFrame(true, 0, ByteReadPacket.Empty))
            }
        }
    }
}
