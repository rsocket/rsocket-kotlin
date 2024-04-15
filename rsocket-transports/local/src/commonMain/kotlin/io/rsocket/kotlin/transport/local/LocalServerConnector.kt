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
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal sealed class LocalServerConnector {
    @RSocketTransportApi
    abstract fun connect(
        clientScope: CoroutineScope,
        clientHandler: RSocketConnectionHandler,
        serverScope: CoroutineScope,
        serverHandler: RSocketConnectionHandler,
    ): Job

    internal class Sequential(
        private val prioritizationQueueBuffersCapacity: Int,
    ) : LocalServerConnector() {

        @RSocketTransportApi
        override fun connect(
            clientScope: CoroutineScope,
            clientHandler: RSocketConnectionHandler,
            serverScope: CoroutineScope,
            serverHandler: RSocketConnectionHandler,
        ): Job {
            val clientToServer = PrioritizationFrameQueue(prioritizationQueueBuffersCapacity)
            val serverToClient = PrioritizationFrameQueue(prioritizationQueueBuffersCapacity)

            launchLocalConnection(serverScope, serverToClient, clientToServer, serverHandler)
            return launchLocalConnection(clientScope, clientToServer, serverToClient, clientHandler)
        }

        @RSocketTransportApi
        private fun launchLocalConnection(
            scope: CoroutineScope,
            outbound: PrioritizationFrameQueue,
            inbound: PrioritizationFrameQueue,
            handler: RSocketConnectionHandler,
        ): Job = scope.launch {
            handler.handleConnection(Connection(outbound, inbound))
        }.onCompletion {
            outbound.close()
            inbound.cancel()
        }

        @RSocketTransportApi
        private class Connection(
            private val outbound: PrioritizationFrameQueue,
            private val inbound: PrioritizationFrameQueue,
        ) : RSocketSequentialConnection {
            override val isClosedForSend: Boolean get() = outbound.isClosedForSend

            override suspend fun sendFrame(streamId: Int, frame: ByteReadPacket) {
                return outbound.enqueueFrame(streamId, frame)
            }

            override suspend fun receiveFrame(): ByteReadPacket? {
                return inbound.dequeueFrame()
            }
        }
    }

    // TODO: better parameters naming
    class Multiplexed(
        private val streamsQueueCapacity: Int,
        private val streamBufferCapacity: Int,
    ) : LocalServerConnector() {
        @RSocketTransportApi
        override fun connect(
            clientScope: CoroutineScope,
            clientHandler: RSocketConnectionHandler,
            serverScope: CoroutineScope,
            serverHandler: RSocketConnectionHandler,
        ): Job {
            val streams = Channels<Channels<ByteReadPacket>>(streamsQueueCapacity)

            launchLocalConnection(serverScope, streams.serverToClient, streams.clientToServer, serverHandler)
            return launchLocalConnection(clientScope, streams.clientToServer, streams.serverToClient, clientHandler)
        }

        @RSocketTransportApi
        private fun launchLocalConnection(
            scope: CoroutineScope,
            outbound: SendChannel<Channels<ByteReadPacket>>,
            inbound: ReceiveChannel<Channels<ByteReadPacket>>,
            handler: RSocketConnectionHandler,
        ): Job = scope.launch {
            handler.handleConnection(Connection(SupervisorJob(coroutineContext.job), outbound, inbound, streamBufferCapacity))
        }.onCompletion {
            outbound.close()
            inbound.cancel()
        }

        @RSocketTransportApi
        private class Connection(
            private val streamsJob: Job,
            private val outbound: SendChannel<Channels<ByteReadPacket>>,
            private val inbound: ReceiveChannel<Channels<ByteReadPacket>>,
            private val streamBufferCapacity: Int,
        ) : RSocketMultiplexedConnection {
            override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
                val frames = Channels<ByteReadPacket>(streamBufferCapacity)

                outbound.send(frames)

                return Stream(
                    parentJob = streamsJob,
                    outbound = frames.clientToServer,
                    inbound = frames.serverToClient
                )
            }

            override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
                val frames = inbound.receiveCatching().getOrNull() ?: return null

                return Stream(
                    parentJob = streamsJob,
                    outbound = frames.serverToClient,
                    inbound = frames.clientToServer
                )
            }
        }

        @RSocketTransportApi
        private class Stream(
            parentJob: Job,
            private val outbound: SendChannel<ByteReadPacket>,
            private val inbound: ReceiveChannel<ByteReadPacket>,
        ) : RSocketMultiplexedConnection.Stream {
            private val streamJob = Job(parentJob).onCompletion {
                outbound.close()
                inbound.cancel()
            }

            override fun close() {
                streamJob.complete()
            }

            @OptIn(DelicateCoroutinesApi::class)
            override val isClosedForSend: Boolean get() = outbound.isClosedForSend

            override fun setSendPriority(priority: Int) {}

            override suspend fun sendFrame(frame: ByteReadPacket) {
                return outbound.send(frame)
            }

            override suspend fun receiveFrame(): ByteReadPacket? {
                return inbound.receiveCatching().getOrNull()
            }
        }

        private class Channels<T : Closeable>(bufferCapacity: Int) : Closeable {
            val clientToServer = channelForCloseable<T>(bufferCapacity)
            val serverToClient = channelForCloseable<T>(bufferCapacity)

            // only for undelivered element case
            override fun close() {
                clientToServer.cancel()
                serverToClient.cancel()
            }
        }
    }
}