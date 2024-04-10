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

package io.rsocket.kotlin.connection

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*

@RSocketLoggingApi
@RSocketTransportApi
internal fun RSocketConnectionHandler.logging(logger: Logger, bufferPool: BufferPool): RSocketConnectionHandler {
    if (!logger.isLoggable(LoggingLevel.DEBUG)) return this

    return RSocketConnectionHandler {
        handleConnection(
            when (it) {
                is RSocketSequentialConnection  -> SequentialLoggingConnection(it, logger, bufferPool)
                is RSocketMultiplexedConnection -> MultiplexedLoggingConnection(it, logger, bufferPool)
            }
        )
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class SequentialLoggingConnection(
    private val delegate: RSocketSequentialConnection,
    private val logger: Logger,
    private val bufferPool: BufferPool,
) : RSocketSequentialConnection {
    override val isClosedForSend: Boolean get() = delegate.isClosedForSend

    override suspend fun sendFrame(streamId: Int, frame: ByteReadPacket) {
        logger.debug { "Send:    ${dumpFrameToString(frame, bufferPool)}" }
        delegate.sendFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): ByteReadPacket? {
        return delegate.receiveFrame()?.also { frame ->
            logger.debug { "Receive: ${dumpFrameToString(frame, bufferPool)}" }
        }
    }

}

private fun dumpFrameToString(frame: ByteReadPacket, bufferPool: BufferPool): String {
    val length = frame.remaining
    return frame.copy().use { it.readFrame(bufferPool).use { it.dump(length) } }
}

@RSocketLoggingApi
@RSocketTransportApi
private class MultiplexedLoggingConnection(
    private val delegate: RSocketMultiplexedConnection,
    private val logger: Logger,
    private val bufferPool: BufferPool,
) : RSocketMultiplexedConnection {
    override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
        return MultiplexedLoggingStream(delegate.createStream(), logger, bufferPool)
    }

    override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
        return delegate.acceptStream()?.let {
            MultiplexedLoggingStream(it, logger, bufferPool)
        }
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class MultiplexedLoggingStream(
    private val delegate: RSocketMultiplexedConnection.Stream,
    private val logger: Logger,
    private val bufferPool: BufferPool,
) : RSocketMultiplexedConnection.Stream {
    override val isClosedForSend: Boolean get() = delegate.isClosedForSend

    override fun setSendPriority(priority: Int) {
        delegate.setSendPriority(priority)
    }

    override suspend fun sendFrame(frame: ByteReadPacket) {
        logger.debug { "Send:    ${dumpFrameToString(frame, bufferPool)}" }
        delegate.sendFrame(frame)
    }

    override suspend fun receiveFrame(): ByteReadPacket? {
        return delegate.receiveFrame()?.also { frame ->
            logger.debug { "Receive: ${dumpFrameToString(frame, bufferPool)}" }
        }
    }

    override fun close() {
        delegate.close()
    }
}
