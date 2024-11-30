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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.rsocket.kotlin.internal.io.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

@Suppress("DEPRECATION_ERROR")
internal class TcpConnection(
    socket: Socket,
    override val coroutineContext: CoroutineContext,
) : io.rsocket.kotlin.Connection {
    private val socketConnection = socket.connection()

    private val sendChannel = bufferChannel(8)
    private val receiveChannel = bufferChannel(8)

    init {
        launch {
            socketConnection.output.use {
                while (isActive) {
                    val packet = sendChannel.receive()
                    val temp = Buffer().also(packet::transferTo)
                    val length = temp.size.toInt()
                    try {
                        writeBuffer(Buffer().apply { writeInt24(length) })
                        writeBuffer(temp)
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
                while (isActive) {
                    val length = readPacket(3).readInt24()
                    val packet = readBuffer(length)
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
            sendChannel.cancelWithCause(it)
            receiveChannel.cancelWithCause(it)
            socketConnection.input.cancel(it)
            socketConnection.output.close(it)
            socketConnection.socket.close()
        }
    }

    override suspend fun send(packet: Buffer): Unit = sendChannel.send(packet)
    override suspend fun receive(): Buffer = receiveChannel.receive()
}
