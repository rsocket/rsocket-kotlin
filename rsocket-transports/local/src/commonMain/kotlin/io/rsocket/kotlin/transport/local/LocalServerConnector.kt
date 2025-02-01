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
import kotlin.coroutines.*

internal sealed class LocalServerConnector {
    @RSocketTransportApi
    abstract suspend fun connect(
        connectionContext: LocalConnectionContext,
        serverConnections: SendChannel<RSocketConnection<LocalConnectionContext>>,
        clientScope: CoroutineScope,
        serverScope: CoroutineScope,
    ): RSocketConnection<LocalConnectionContext>

    object Sequential : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            connectionContext: LocalConnectionContext,
            serverConnections: SendChannel<RSocketConnection<LocalConnectionContext>>,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketConnection<LocalConnectionContext> {
            val frames = Frames()
            val serverConnection = Connection(serverScope, connectionContext, frames.serverToClient, frames.clientToServer)
            try {
                serverConnections.send(serverConnection)
            } catch (cause: Throwable) {
                serverConnection.cancel("Connection establishment failed", cause)
                frames.close()
                throw cause
            }
            return Connection(clientScope, connectionContext, frames.serverToClient, frames.serverToClient)
        }

        @RSocketTransportApi
        private class Connection(
            parentScope: CoroutineScope,
            override val connectionContext: LocalConnectionContext,
            private val incomingFrames: ReceiveChannel<Buffer>,
            private val outgoingFrames: SendChannel<Buffer>,
        ) : RSocketSequentialConnection<LocalConnectionContext> {
            private val outboundQueue = PrioritizationFrameQueue(Channel.BUFFERED)
            override val coroutineContext: CoroutineContext = parentScope.coroutineContext + parentScope.launch(Dispatchers.Unconfined) {
                launch {
                    nonCancellable {
                        while (true) outgoingFrames.send(outboundQueue.dequeueFrame() ?: break)
                    }
                }.invokeOnCompletion {
                    outboundQueue.cancel()
                    outgoingFrames.close()
                }
                try {
                    awaitCancellation()
                } finally {
                    outboundQueue.close()
                    incomingFrames.cancel()
                }
            }

            override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

            override suspend fun sendFrame(streamId: Int, frame: Buffer) {
                return outboundQueue.enqueueFrame(streamId, frame)
            }

            override suspend fun receiveFrame(): Buffer? {
                return incomingFrames.receiveCatching().getOrNull()
            }
        }
    }

    object Multiplexed : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            connectionContext: LocalConnectionContext,
            serverConnections: SendChannel<RSocketConnection<LocalConnectionContext>>,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketConnection<LocalConnectionContext> {
            val streams = Streams()

            val serverConnection = Connection(
                parentContext = serverScope.coroutineContext,
                connectionContext = connectionContext,
                incomingStreams = streams.clientToServer,
                outgoingStreams = streams.serverToClient
            )
            try {
                serverConnections.send(serverConnection)
            } catch (cause: Throwable) {
                serverConnection.cancel("Connection establishment failed", cause)
                streams.close()
                throw cause
            }
            return Connection(
                parentContext = clientScope.coroutineContext,
                connectionContext = connectionContext,
                incomingStreams = streams.serverToClient,
                outgoingStreams = streams.clientToServer
            )
        }

        @RSocketTransportApi
        private class Connection(
            parentContext: CoroutineContext,
            override val connectionContext: LocalConnectionContext,
            private val incomingStreams: ReceiveChannel<Frames>,
            private val outgoingStreams: SendChannel<Frames>,
        ) : RSocketMultiplexedConnection<LocalConnectionContext> {
            override val coroutineContext: CoroutineContext = parentContext.childContext().also {
                it.job.invokeOnCompletion {
                    outgoingStreams.close()
                    incomingStreams.cancel()
                }
            }
            private val streamsContext = parentContext.supervisorContext()

            override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
                val frames = Frames()
                outgoingStreams.send(frames)
                return Stream(
                    parentContext = streamsContext,
                    incoming = frames.clientToServer,
                    outgoing = frames.serverToClient
                )
            }

            override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
                val frames = incomingStreams.receiveCatching().getOrNull() ?: return null
                return Stream(
                    parentContext = streamsContext,
                    incoming = frames.serverToClient,
                    outgoing = frames.clientToServer
                )
            }
        }

        @RSocketTransportApi
        private class Stream(
            parentContext: CoroutineContext,
            private val incoming: ReceiveChannel<Buffer>,
            private val outgoing: SendChannel<Buffer>,
        ) : RSocketMultiplexedConnection.Stream {
            override val coroutineContext: CoroutineContext = parentContext.childContext().also {
                it.job.invokeOnCompletion {
                    outgoing.close()
                    incoming.cancel()
                }
            }

            @OptIn(DelicateCoroutinesApi::class)
            override val isClosedForSend: Boolean get() = outgoing.isClosedForSend

            override fun setSendPriority(priority: Int) {
                // no-op
            }

            override suspend fun sendFrame(frame: Buffer) {
                return outgoing.send(frame)
            }

            override suspend fun receiveFrame(): Buffer? {
                return incoming.receiveCatching().getOrNull()
            }
        }
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
