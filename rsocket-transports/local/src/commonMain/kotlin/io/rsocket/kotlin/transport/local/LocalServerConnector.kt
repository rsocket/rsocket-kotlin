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
        clientContext: CoroutineContext,
        serverContext: CoroutineContext,
        onConnection: (RSocketConnection) -> Unit,
    ): RSocketConnection

    object Sequential : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            clientContext: CoroutineContext,
            serverContext: CoroutineContext,
            onConnection: (RSocketConnection) -> Unit,
        ): RSocketConnection {
            val frames = Frames()
            onConnection(Connection(serverContext.childContext(), frames.clientToServer, frames.serverToClient))
            return Connection(clientContext.childContext(), frames.serverToClient, frames.clientToServer)
        }

        @RSocketTransportApi
        private class Connection(
            override val coroutineContext: CoroutineContext,
            private val incomingFrames: ReceiveChannel<Buffer>,
            private val outgoingFrames: SendChannel<Buffer>,
        ) : RSocketSequentialConnection {
            private val outboundQueue = PrioritizationFrameQueue()

            init {
                @OptIn(DelicateCoroutinesApi::class)
                launch(start = CoroutineStart.ATOMIC) {
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
            }

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
            clientContext: CoroutineContext,
            serverContext: CoroutineContext,
            onConnection: (RSocketConnection) -> Unit,
        ): RSocketConnection {
            val streams = Streams()
            onConnection(Connection(serverContext.childContext(), streams.clientToServer, streams.serverToClient))
            return Connection(clientContext.childContext(), streams.serverToClient, streams.clientToServer)
        }

        @RSocketTransportApi
        private class Connection(
            override val coroutineContext: CoroutineContext,
            private val incomingStreams: ReceiveChannel<Frames>,
            private val outgoingStreams: SendChannel<Frames>,
        ) : RSocketMultiplexedConnection {
            private val streamsContext = coroutineContext.supervisorContext()

            init {
                coroutineContext.job.invokeOnCompletion {
                    outgoingStreams.close()
                    incomingStreams.cancel()
                }
            }

            override suspend fun createStream(): RSocketMultiplexedConnection.Stream {
                val frames = Frames()
                outgoingStreams.send(frames)
                return Stream(
                    coroutineContext = streamsContext.childContext(),
                    incoming = frames.clientToServer,
                    outgoing = frames.serverToClient
                )
            }

            override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? {
                val frames = incomingStreams.receiveCatching().getOrNull() ?: return null
                return Stream(
                    coroutineContext = streamsContext.childContext(),
                    incoming = frames.serverToClient,
                    outgoing = frames.clientToServer
                )
            }
        }

        @RSocketTransportApi
        private class Stream(
            override val coroutineContext: CoroutineContext,
            private val incoming: ReceiveChannel<Buffer>,
            private val outgoing: SendChannel<Buffer>,
        ) : RSocketMultiplexedConnection.Stream {
            init {
                coroutineContext.job.invokeOnCompletion {
                    outgoing.close()
                    incoming.cancel()
                }
            }

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
