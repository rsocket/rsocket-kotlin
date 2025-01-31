/*
 * Copyright 2015-2025 the original author or authors.
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

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*

internal sealed class LocalServerConnector {
    @RSocketTransportApi
    abstract fun connect(
        clientScope: CoroutineScope,
        clientHandler: RSocketConnectionHandler,
        serverScope: CoroutineScope,
        serverHandler: RSocketConnectionHandler,
    ): Job

    object Sequential : LocalServerConnector() {

        @RSocketTransportApi
        override fun connect(
            clientScope: CoroutineScope,
            clientHandler: RSocketConnectionHandler,
            serverScope: CoroutineScope,
            serverHandler: RSocketConnectionHandler,
        ): Job {
            val clientToServer = PrioritizationFrameQueue(Channel.BUFFERED)
            val serverToClient = PrioritizationFrameQueue(Channel.BUFFERED)

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

            override suspend fun sendFrame(streamId: Int, frame: Buffer) {
                return outbound.enqueueFrame(streamId, frame)
            }

            override suspend fun receiveFrame(): Buffer? {
                return inbound.dequeueFrame()
            }
        }
    }

    object Multiplexed : LocalServerConnector() {
        @RSocketTransportApi
        override fun connect(
            clientScope: CoroutineScope,
            clientHandler: RSocketConnectionHandler,
            serverScope: CoroutineScope,
            serverHandler: RSocketConnectionHandler,
        ): Job {
            val streams = Streams()

            launchLocalConnection(serverScope, streams.serverToClient, streams.clientToServer, serverHandler)
            return launchLocalConnection(clientScope, streams.clientToServer, streams.serverToClient, clientHandler)
        }

        @RSocketTransportApi
        private fun launchLocalConnection(
            scope: CoroutineScope,
            outbound: SendChannel<Frames>,
            inbound: ReceiveChannel<Frames>,
            handler: RSocketConnectionHandler,
        ): Job = scope.launch {
            handler.handleConnection(Connection(SupervisorJob(coroutineContext.job), outbound, inbound))
        }.onCompletion {
            outbound.close()
            inbound.cancel()
        }

        @RSocketTransportApi
        private class Connection(
            private val streamsJob: Job,
            private val outbound: SendChannel<Frames>,
            private val inbound: ReceiveChannel<Frames>,
        ) : RSocketMultiplexedConnection {
            override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
                val frames = Frames()

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
            private val outbound: SendChannel<Buffer>,
            private val inbound: ReceiveChannel<Buffer>,
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

            override suspend fun sendFrame(frame: Buffer) {
                return outbound.send(frame)
            }

            override suspend fun receiveFrame(): Buffer? {
                return inbound.receiveCatching().getOrNull()
            }
        }

        private class Streams : AutoCloseable {
            val clientToServer = channelForCloseable<Frames>(Channel.BUFFERED)
            val serverToClient = channelForCloseable<Frames>(Channel.BUFFERED)

            // only for undelivered element case
            override fun close() {
                clientToServer.cancel()
                serverToClient.cancel()
            }
        }

        private class Frames : AutoCloseable {
            val clientToServer = bufferChannel(Channel.BUFFERED)
            val serverToClient = bufferChannel(Channel.BUFFERED)

            // only for undelivered element case
            override fun close() {
                clientToServer.cancel()
                serverToClient.cancel()
            }
        }
    }
}
