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

package io.rsocket.connection

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.rsocket.frame.io.*
import kotlinx.coroutines.*

//TODO need to check and extract length support!!
@OptIn(KtorExperimentalAPI::class)
class KtorTcpConnection(private val socket: Socket) : Connection {
    override val job: Job get() = socket.socketContext

    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(true)

    override suspend fun send(bytes: ByteArray) {
        val length = bytes.size
        writeChannel.writePacket {
            writeLength(length)
            writeFully(bytes)
        }
    }

    override suspend fun receive(): ByteArray = readChannel.run {
        val length = readPacket(3).readLength()
        val bytes = readPacket(length).readBytes()
        bytes
    }
}
