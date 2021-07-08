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

@file:OptIn(TransportApi::class)

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.logging.*

internal fun Connection.logging(logger: Logger): Connection =
    if (logger.isLoggable(LoggingLevel.DEBUG)) LoggingConnection(this, logger) else this

@OptIn(DangerousInternalIoApi::class)
private class LoggingConnection(
    private val delegate: Connection,
    private val logger: Logger,
) : Connection by delegate {

    private fun ByteReadPacket.dumpFrameToString(): String {
        val length = remaining
        return copy().use { it.readFrame(pool).use { it.dump(length) } }
    }

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
