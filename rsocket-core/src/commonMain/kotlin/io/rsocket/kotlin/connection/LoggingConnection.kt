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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.io.*
import kotlin.coroutines.*

@RSocketLoggingApi
@RSocketTransportApi
internal fun RSocketConnectionOutbound.logging(logger: Logger): RSocketConnectionOutbound {
    if (!logger.isLoggable(LoggingLevel.DEBUG)) return this
    return LoggingConnectionOutbound(this, logger)
}

private fun dumpFrameToString(frame: Buffer): String {
    val length = frame.size
    return frame.copy().readFrame().use { it.dump(length) }
}

@RSocketLoggingApi
@RSocketTransportApi
private class LoggingConnectionOutbound(
    private val delegate: RSocketConnectionOutbound,
    private val logger: Logger,
) : RSocketConnectionOutbound {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    override suspend fun sendFrame(frame: Buffer) {
        logger.debug { "Send:    ${dumpFrameToString(frame)}" }
        delegate.sendFrame(frame)
    }

    override suspend fun createStream(): RSocketStreamOutbound {
        return LoggingStreamOutbound(delegate.createStream(), logger)
    }

    override fun close(cause: Throwable?) {
        delegate.close(cause)
    }

    override fun startReceiving(inbound: RSocketConnectionInbound) {
        delegate.startReceiving(LoggingConnectionInbound(inbound, logger))
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class LoggingConnectionInbound(
    private val delegate: RSocketConnectionInbound,
    private val logger: Logger,
) : RSocketConnectionInbound {
    override fun onFrame(frame: Buffer) {
        logger.debug { "Receive: ${dumpFrameToString(frame)}" }
        delegate.onFrame(frame)
    }

    override fun onStream(frame: Buffer, stream: RSocketStreamOutbound) {
        logger.debug { "Receive: ${dumpFrameToString(frame)}" }
        delegate.onStream(frame, LoggingStreamOutbound(stream, logger))
    }

    override fun onClose(cause: Throwable?) {
        delegate.onClose(cause)
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class LoggingStreamOutbound(
    private val delegate: RSocketStreamOutbound,
    private val logger: Logger,
) : RSocketStreamOutbound {
    override val streamId: Int get() = delegate.streamId
    override val isClosedForSend: Boolean get() = delegate.isClosedForSend

    override suspend fun sendFrame(frame: Buffer) {
        logger.debug { "Send:    ${dumpFrameToString(frame)}" }
        delegate.sendFrame(frame)
    }

    override fun startReceiving(inbound: RSocketStreamInbound) {
        delegate.startReceiving(LoggingStreamInbound(inbound, logger))
    }

    override fun close(cause: Throwable?) {
        delegate.close(cause)
    }
}

@RSocketLoggingApi
@RSocketTransportApi
private class LoggingStreamInbound(
    private val delegate: RSocketStreamInbound,
    private val logger: Logger,
) : RSocketStreamInbound {
    override fun onFrame(frame: Buffer) {
        logger.debug { "Receive: ${dumpFrameToString(frame)}" }
        delegate.onFrame(frame)
    }

    override fun onClose(cause: Throwable?) {
        delegate.onClose(cause)
    }
}
