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

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.io.*
import kotlinx.coroutines.*

val Socket.connection: Connection get() = KtorTcpConnection(this)

//TODO need to check and extract length support!!
@OptIn(KtorExperimentalAPI::class)
private class KtorTcpConnection(private val socket: Socket) : Connection {
    override val job: Job get() = socket.socketContext

    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(true)

    override suspend fun send(packet: ByteReadPacket): Unit = writeChannel.run {
        val length = packet.remaining.toInt()
        writePacket { writeLength(length) }
        writePacket(packet)
    }

    override suspend fun receive(): ByteReadPacket = readChannel.run {
        val length = readPacket(3).readLength()
        readPacket(length)
    }
}
