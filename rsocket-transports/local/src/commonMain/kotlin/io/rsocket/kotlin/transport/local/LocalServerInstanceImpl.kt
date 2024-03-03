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

package io.rsocket.kotlin.transport.local

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.random.*

internal class LocalServerInstanceImpl @RSocketTransportApi constructor(
    override val serverName: String,
    override val coroutineContext: CoroutineContext,
    private val connectionBufferCapacity: Int,
    private val acceptor: RSocketServerAcceptor,
) : LocalServerInstance {

    init {
        register(serverName, this)
        coroutineContext.job.invokeOnCompletion { unregister(serverName) }
    }

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession = connect(coroutineContext)

    @RSocketTransportApi
    fun connect(clientContext: CoroutineContext): RSocketTransportSession {
        ensureActive()

        val clientToServer = channelForCloseable<ByteReadPacket>(connectionBufferCapacity)
        val serverToClient = channelForCloseable<ByteReadPacket>(connectionBufferCapacity)

        launch {
            acceptor.acceptSession(
                LocalSequentialSession(
                    coroutineContext = coroutineContext.childContext(),
                    outbound = serverToClient,
                    inbound = clientToServer
                )
            )
        }

        return LocalSequentialSession(
            coroutineContext = clientContext.childContext(),
            outbound = clientToServer,
            inbound = serverToClient
        )
    }

    companion object {
        private val lock = SynchronizedObject()
        private val instances = mutableMapOf<String, LocalServerInstanceImpl>()

        private fun register(name: String, target: LocalServerInstanceImpl): Unit = synchronized(lock) {
            check(name !in instances) { "Already registered: $name" }
            instances[name] = target
        }

        private fun unregister(name: String): Unit = synchronized(lock) {
            instances.remove(name)
        }

        fun get(name: String): LocalServerInstanceImpl = synchronized(lock) {
            checkNotNull(instances[name]) { "Cannot find $name" }
        }

        @OptIn(ExperimentalStdlibApi::class)
        fun randomName(): String = Random.nextBytes(16).toHexString(HexFormat.UpperCase)
    }
}

@RSocketTransportApi
private class LocalSequentialSession(
    override val coroutineContext: CoroutineContext,
    private val outbound: SendChannel<ByteReadPacket>,
    private val inbound: ReceiveChannel<ByteReadPacket>,
) : RSocketTransportSession.Sequential {

    init {
        coroutineContext.job.invokeOnCompletion {
            outbound.close(it)
            inbound.cancel(CancellationException("Local connection closed", it))
        }
    }

    override suspend fun sendFrame(frame: ByteReadPacket) {
        outbound.send(frame)
    }

    override suspend fun receiveFrame(): ByteReadPacket {
        return inbound.receive()
    }
}
