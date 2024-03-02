/*
 * Copyright 2015-2023 the original author or authors.
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

package io.rsocket.kotlin.transport.nodejs.tcp

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.js.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.khronos.webgl.*
import kotlin.coroutines.*

@TransportApi
internal class TcpConnection(
    override val coroutineContext: CoroutineContext,
    override val pool: ObjectPool<ChunkBuffer>,
    private val socket: Socket,
) : Connection {

    private val sendChannel = channelForCloseable<ByteReadPacket>(8)
    private val receiveChannel = channelForCloseable<ByteReadPacket>(Channel.UNLIMITED)

    init {
        launch {
            sendChannel.consumeEach { packet ->
                socket.write(Uint8Array(packet.withLength().readArrayBuffer()))
            }
        }

        coroutineContext.job.invokeOnCompletion {
            when (it) {
                null -> socket.destroy()
                else -> socket.destroy(Error(it.message, it.cause))
            }
        }

        val frameAssembler = FrameWithLengthAssembler { receiveChannel.trySend(it) } //TODO
        socket.on(
            onData = { frameAssembler.write { writeFully(it.buffer) } },
            onError = { coroutineContext.job.cancel("Socket error", it) },
            onClose = { if (!it) coroutineContext.job.cancel("Socket closed") }
        )
    }

    override suspend fun send(packet: ByteReadPacket) {
        sendChannel.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        return receiveChannel.receive()
    }
}
