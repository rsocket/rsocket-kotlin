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

package io.rsocket.kotlin.connection

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.logging.*
import kotlinx.coroutines.*

internal fun Connection.logging(logger: Logger): Connection =
    if (logger.isLoggable(LoggingLevel.DEBUG)) LoggingConnection(this, logger) else this

private class LoggingConnection(
    private val delegate: Connection,
    private val logger: Logger,
) : Connection {
    override val job: Job get() = delegate.job

    override suspend fun send(packet: ByteReadPacket) {
        logger.debug { "Send: ${packet.dumpFrameToString()}" }
        delegate.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        val packet = delegate.receive()
        logger.debug { "Receive: ${packet.dumpFrameToString()}" }
        return packet
    }
}
