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
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class KtorTcpSession(
    override val coroutineContext: CoroutineContext,
    socket: Socket,
) : RSocketTransportSession.Sequential {
    //TODO what is the best way to configure it?
    private val socketConnection = socket.connection()

    init {
        coroutineContext.job.invokeOnCompletion {
            socketConnection.input.cancel(it)
            socketConnection.output.close(it)
            socketConnection.socket.close()
        }
    }

    override suspend fun sendFrame(frame: ByteReadPacket): Unit = with(socketConnection.output) {
        writePacket(
            buildPacket {
                writeInt24(frame.remaining.toInt())
                writePacket(frame)
            }
        )
        flush()
    }

    override suspend fun receiveFrame(): ByteReadPacket = with(socketConnection.input) {
        val length = readPacket(3).readInt24()
        readPacket(length)
    }
}
