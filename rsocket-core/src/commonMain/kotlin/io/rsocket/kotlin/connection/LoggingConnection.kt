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
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.io.*
import kotlin.coroutines.*

@RSocketLoggingApi
@RSocketTransportApi
internal fun RSocketConnection.logging(logger: Logger): RSocketConnection {
    if (!logger.isLoggable(LoggingLevel.DEBUG)) return this

    return when (this) {
        is RSocketSequentialConnection  -> SequentialLoggingConnection(this, logger)
        is RSocketMultiplexedConnection -> MultiplexedLoggingConnection(this, logger)
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class SequentialLoggingConnection(
    private val delegate: RSocketSequentialConnection,
    private val logger: Logger,
) : RSocketSequentialConnection {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    override suspend fun sendFrame(streamId: Int, frame: Buffer) {
        logger.debug { "Send:    ${dumpFrameToString(frame)}" }
        delegate.sendFrame(streamId, frame)
    }

    override suspend fun receiveFrame(): Buffer? {
        return delegate.receiveFrame()?.also { frame ->
            logger.debug { "Receive: ${dumpFrameToString(frame)}" }
        }
    }

}

private fun dumpFrameToString(frame: Buffer): String {
    val length = frame.size
    return frame.copy().readFrame().use { it.dump(length) }
}

@RSocketLoggingApi
@RSocketTransportApi
private class MultiplexedLoggingConnection(
    private val delegate: RSocketMultiplexedConnection,
    private val logger: Logger,
) : RSocketMultiplexedConnection {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
        return MultiplexedLoggingStream(delegate.createStream(), logger)
    }

    override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
        return delegate.acceptStream()?.let {
            MultiplexedLoggingStream(it, logger)
        }
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class MultiplexedLoggingStream(
    private val delegate: RSocketMultiplexedConnection.Stream,
    private val logger: Logger,
) : RSocketMultiplexedConnection.Stream {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    override fun setSendPriority(priority: Int) {
        delegate.setSendPriority(priority)
    }

    override suspend fun sendFrame(frame: Buffer) {
        logger.debug { "Send:    ${dumpFrameToString(frame)}" }
        delegate.sendFrame(frame)
    }

    override suspend fun receiveFrame(): Buffer? {
        return delegate.receiveFrame()?.also { frame ->
            logger.debug { "Receive: ${dumpFrameToString(frame)}" }
        }
    }
}
