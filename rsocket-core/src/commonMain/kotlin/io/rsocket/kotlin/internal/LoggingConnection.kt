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
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlin.coroutines.*

@RSocketLoggingApi
@RSocketTransportApi
internal fun RSocketTransportSession.logging(logger: Logger, bufferPool: BufferPool): RSocketTransportSession {
    if (!logger.isLoggable(LoggingLevel.DEBUG)) return this

    return when (this) {
        is RSocketTransportSession.Sequential -> SequentialLoggingConnection(this, logger, bufferPool)
        else                                  -> TODO("not yet supported")
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class SequentialLoggingConnection(
    private val delegate: RSocketTransportSession.Sequential,
    private val logger: Logger,
    private val bufferPool: BufferPool,
) : RSocketTransportSession.Sequential {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    private fun ByteReadPacket.dumpFrameToString(): String {
        val length = remaining
        return copy().use { it.readFrame(bufferPool).use { it.dump(length) } }
    }

    override suspend fun sendFrame(frame: ByteReadPacket) {
        logger.debug { "Send:    ${frame.dumpFrameToString()}" }
        delegate.sendFrame(frame)
    }

    override suspend fun receiveFrame(): ByteReadPacket {
        val frame = delegate.receiveFrame()
        logger.debug { "Receive: ${frame.dumpFrameToString()}" }
        return frame
    }
}
