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

import app.cash.turbine.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*
import kotlin.time.*

class TestConnection : Connection, CoroutineScope {
    override val pool: ObjectPool<ChunkBuffer> = InUseTrackingPool
    override val job: Job = Job()
    override val coroutineContext: CoroutineContext = job + Dispatchers.Unconfined

    private val sendChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)
    private val receiveChannel = Channel<ByteReadPacket>(Channel.UNLIMITED)

    init {
        job.invokeOnCompletion {
            sendChannel.close(it)
            receiveChannel.cancel(it?.let { it as? CancellationException ?: CancellationException("Connection completed") })
        }
    }

    override suspend fun send(packet: ByteReadPacket) {
        sendChannel.send(packet)
    }

    override suspend fun receive(): ByteReadPacket {
        return receiveChannel.receive()
    }

    suspend fun sendToReceiver(vararg frames: Frame) {
        frames.forEach { receiveChannel.send(it.toPacket(InUseTrackingPool)) }
    }

    private fun sentAsFlow(): Flow<Frame> = sendChannel.receiveAsFlow().map { it.readFrame(InUseTrackingPool) }

    suspend fun test(validate: suspend FlowTurbine<Frame>.() -> Unit) {
        sentAsFlow().test(validate = validate)
    }
}

suspend fun FlowTurbine<*>.expectNoEventsIn(duration: Duration) {
    delay(duration)
    expectNoEvents()
}

suspend fun FlowTurbine<*>.expectNoEventsIn(timeMillis: Long) {
    delay(timeMillis)
    expectNoEvents()
}

suspend inline fun FlowTurbine<Frame>.expectFrame(block: (frame: Frame) -> Unit) {
    block(expectItem())
}
