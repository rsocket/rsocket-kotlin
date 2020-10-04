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

package io.rsocket.kotlin.test

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.frame.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

class TestConnection : Connection {
    override val job: Job = Job()
    private val sender = Channel<ByteReadPacket>(Channel.UNLIMITED)
    private val receiver = Channel<ByteReadPacket>(Channel.UNLIMITED)

    private val store = TestPacketStore()
    val sentFrames: List<Frame> get() = store.stored.map { it.copy().toFrame() }

    init {
        job.invokeOnCompletion {
            sender.close(it)
            receiver.cancel(it?.let { it as? CancellationException ?: CancellationException("Connection completed") })
        }
    }

    override suspend fun send(packet: ByteReadPacket) {
        sender.send(packet)
        store.store(packet.copy())
    }

    override suspend fun receive(): ByteReadPacket {
        return receiver.receive()
    }

    suspend fun receiveFromSender() = sender.receive().toFrame()

    suspend fun sendToReceiver(vararg frames: Frame) {
        frames.forEach { receiver.send(it.toPacket()) }
    }

    fun sentAsFlow() = sender.receiveAsFlow().map { it.toFrame() }
}
