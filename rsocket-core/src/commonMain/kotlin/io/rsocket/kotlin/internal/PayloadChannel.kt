/*
 * Copyright 2015-2025 the original author or authors.
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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

internal class PayloadChannel {
    private val payloads = channelForCloseable<Payload>(Channel.UNLIMITED)
    private val requestNs = Channel<Int>(Channel.UNLIMITED)

    suspend fun nextRequestN(): Int? = requestNs.receiveCatching().getOrNull()

    @OptIn(DelicateCoroutinesApi::class)
    val isActive: Boolean get() = !payloads.isClosedForSend

    fun trySend(payload: Payload) {
        if (payloads.trySend(payload).isFailure) payload.close()
    }

    @ExperimentalStreamsApi
    suspend fun consumeInto(collector: FlowCollector<Payload>, strategy: RequestStrategy.Element): Throwable? {
        payloads.consume {
            while (true) {
                payloads
                    .receiveCatching()
                    .onClosed { return it }
                    .getOrThrow() // will never throw
                    .also { collector.emit(it) } // emit frame

                @OptIn(DelicateCoroutinesApi::class)
                if (requestNs.isClosedForSend) continue

                val next = strategy.nextRequest()
                if (next <= 0) continue

                // if this fails, it's means that requests no longer possible;
                // next payloads.receiveCatching() should return a closed state
                requestNs.trySend(next)
            }
        }
    }

    fun close(cause: Throwable?) {
        requestNs.cancel()
        payloads.close(cause)
    }
}
