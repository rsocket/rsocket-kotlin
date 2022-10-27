/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.Connection
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

@TransportApi
internal class TcpConnection(
    socket: Socket,
    override val coroutineContext: CoroutineContext,
    override val pool: ObjectPool<ChunkBuffer>,
) : Connection {
    private val socketConnection = socket.connection()

    private val sendChannel = @Suppress("INVISIBLE_MEMBER") SafeChannel<ByteReadPacket>(8)
    private val receiveChannel = @Suppress("INVISIBLE_MEMBER") SafeChannel<ByteReadPacket>(8)

    init {
        launch {
            socketConnection.output.use {
                while (true) {
                    val packet = sendChannel.receiveCatching().getOrNull() ?: break
                    val length = packet.remaining.toInt()
                    try {
                        writePacket {
                            @Suppress("INVISIBLE_MEMBER") writeLength(length)
                            writePacket(packet)
                        }
                        flush()
                    } catch (e: Throwable) {
                        packet.close()
                        throw e
                    }
                }
            }
        }
        launch {
            socketConnection.input.apply {
                while (true) {
                    val length = @Suppress("INVISIBLE_MEMBER") readPacketOrNull(3)?.readLength() ?: break
                    val packet = readPacketOrNull(length.toLong()) ?: break
                    try {
                        receiveChannel.send(packet)
                    } catch (cause: Throwable) {
                        packet.close()
                        throw cause
                    }
                }
            }
        }
        coroutineContext.job.invokeOnCompletion {
            @Suppress("INVISIBLE_MEMBER") sendChannel.fullClose(it)
            @Suppress("INVISIBLE_MEMBER") receiveChannel.fullClose(it)
            socketConnection.input.cancel(it)
            socketConnection.output.close(it)
            socketConnection.socket.close()
        }
    }

    private suspend fun ByteReadChannel.readPacketOrNull(size: Long): ByteReadPacket? {
        val packet = readRemaining(size)
        if (packet.remaining == size) return packet
        packet.close()
        return null
    }

    override suspend fun send(packet: ByteReadPacket): Boolean {
        return try {
            sendChannel.send(packet)
            true
        } catch (cause: ClosedSendChannelException) {
            false
        }
    }

    override suspend fun receive(): ByteReadPacket? {
        return receiveChannel.receiveCatching().onClosed { it?.let { throw it } }.getOrNull()
    }
}
