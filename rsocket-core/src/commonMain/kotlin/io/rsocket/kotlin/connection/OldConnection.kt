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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

@Suppress("DEPRECATION_ERROR")
@RSocketTransportApi
internal class OldConnection(
    private val connection: Connection,
) : RSocketSequentialConnection {
    private val outboundQueue = PrioritizationFrameQueue()

    override val coroutineContext: CoroutineContext get() = connection.coroutineContext

    init {
        @OptIn(DelicateCoroutinesApi::class)
        launch(start = CoroutineStart.ATOMIC) {
            launch {
                nonCancellable {
                    while (true) {
                        connection.send(outboundQueue.dequeueFrame() ?: break)
                    }
                }
            }.onCompletion {
                outboundQueue.cancel()
            }

            try {
                awaitCancellation()
            } finally {
                outboundQueue.close()
            }
        }
    }

    override suspend fun sendFrame(streamId: Int, frame: Buffer) {
        return outboundQueue.enqueueFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): Buffer? = try {
        connection.receive()
    } catch (_: Throwable) {
        currentCoroutineContext().ensureActive()
        null
    }
}
