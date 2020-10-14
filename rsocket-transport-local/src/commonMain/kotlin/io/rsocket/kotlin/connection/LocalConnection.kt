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
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

@OptIn(DangerousInternalIoApi::class)
fun LocalConnection(
    name: String,
    sender: Channel<ByteReadPacket>,
    receiver: Channel<ByteReadPacket>,
    parentJob: Job? = null,
): LocalConnection = LocalConnection(name, sender, receiver, ChunkBuffer.Pool, parentJob)

class LocalConnection
@DangerousInternalIoApi
internal constructor(
    private val name: String,
    private val sender: Channel<ByteReadPacket>,
    private val receiver: Channel<ByteReadPacket>,
    override val pool: ObjectPool<ChunkBuffer>,
    parentJob: Job? = null,
) : Connection, Cancelable {
    override val job: Job = Job(parentJob)

    init {
        job.invokeOnCompletion {
            sender.closeReceivedElements()
            receiver.closeReceivedElements()
            sender.close(it)
            receiver.close(it)
        }
    }

    override suspend fun send(packet: ByteReadPacket) {
        sender.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        return receiver.receive()
    }
}

@OptIn(DangerousInternalIoApi::class)
@Suppress("FunctionName")
public fun SimpleLocalConnection(parentJob: Job? = null): Pair<LocalConnection, LocalConnection> =
    SimpleLocalConnection(ChunkBuffer.Pool, parentJob)

/**
 * Returns pair of client and server local connections
 */
@Suppress("FunctionName")
@DangerousInternalIoApi
internal fun SimpleLocalConnection(pool: ObjectPool<ChunkBuffer>, parentJob: Job? = null): Pair<LocalConnection, LocalConnection> {
    val clientChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)
    val serverChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)

    val clientConnection = LocalConnection("client", serverChannel, clientChannel, pool, parentJob)
    val serverConnection = LocalConnection("server", clientChannel, serverChannel, pool, parentJob)

    return clientConnection to serverConnection
}

private fun ReceiveChannel<Closeable>.closeReceivedElements() {
    try {
        while (true) poll()?.close() ?: break
    } catch (e: Throwable) {
    }
}
