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
        serverInbound: RSocketServerInstance.Inbound<LocalConnectionContext>,
        clientScope: CoroutineScope,
        serverScope: CoroutineScope,
    ): RSocketConnection<LocalConnectionContext>

    object Sequential : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            connectionContext: LocalConnectionContext,
            serverInbound: RSocketServerInstance.Inbound<LocalConnectionContext>,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketConnection<LocalConnectionContext> {
            val frames = Frames()
            serverInbound.onConnection(Connection(serverScope, connectionContext, frames.serverToClient, frames.clientToServer))
            return Connection(clientScope, connectionContext, frames.serverToClient, frames.serverToClient)
        }

        @RSocketTransportApi
        private class Connection(
            parentScope: CoroutineScope,
            override val connectionContext: LocalConnectionContext,
            private val incomingFrames: ReceiveChannel<Buffer>,
            private val outgoingFrames: SendChannel<Buffer>,
        ) : SequentialRSocketConnection<LocalConnectionContext> {
            private val outboundQueue = PrioritizationFrameQueue()
            private var inboundJob: Job? = null
            private var outboundJob: Job? = null

            override val coroutineContext: CoroutineContext = parentScope.coroutineContext + parentScope.launch(Dispatchers.Unconfined) {
                try {
                    // await connection completion
                    awaitCancellation()
                } finally {
                    // even if it was cancelled, we still need to close socket and await it closure
                    withContext(NonCancellable) {
                        // await inbound completion
                        inboundJob?.cancel()
                        outboundQueue.close() // will cause `writerJob` completion
                        // await completion of read/write jobs
                        inboundJob?.join()
                        outboundJob?.join()
                    }
                }
            }

            override val isClosedForSend: Boolean get() = outboundQueue.isClosedForSend

            init {
                outboundJob = launch(Dispatchers.Unconfined) {
                    try {
                        while (true) {
                            outgoingFrames.send(outboundQueue.dequeueFrame() ?: break)
                        }
                    } finally {
                        outboundQueue.cancel() // cleanup frames
                    }
                }
            }

            override suspend fun sendConnectionFrame(frame: Buffer) {
                outboundQueue.enqueuePriorityFrame(frame)
            }

            override suspend fun sendStreamFrame(frame: Buffer) {
                outboundQueue.enqueueNormalFrame(frame)
            }

            override fun startReceiving(inbound: SequentialRSocketConnection.Inbound) {
                inboundJob = launch(Dispatchers.Unconfined) {
                    incomingFrames.consumeEach(inbound::onFrame)
                }
            }
        }
    }

    object Multiplexed : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            connectionContext: LocalConnectionContext,
            serverInbound: RSocketServerInstance.Inbound<LocalConnectionContext>,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketConnection<LocalConnectionContext> {
            val zeroFrames = Frames()
            val streams = Streams()

            serverInbound.onConnection(
                ConnectionOutbound(
                    parentContext = serverScope.coroutineContext,
                    incomingFrames = zeroFrames.clientToServer,
                    outgoingFrames = zeroFrames.serverToClient,
                    incomingStreams = streams.clientToServer,
                    outgoingStreams = streams.serverToClient
                )
            )

            return ConnectionOutbound(
                parentContext = clientScope.coroutineContext,
                incomingFrames = zeroFrames.serverToClient,
                outgoingFrames = zeroFrames.clientToServer,
                incomingStreams = streams.serverToClient,
                outgoingStreams = streams.clientToServer
            )
        }

        @RSocketTransportApi
        private class ConnectionOutbound(
            parentContext: CoroutineContext,
            private val incomingFrames: ReceiveChannel<Buffer>,
            private val outgoingFrames: SendChannel<Buffer>,
            private val incomingStreams: ReceiveChannel<Frames>,
            private val outgoingStreams: SendChannel<Frames>,
        ) : MultiplexedRSocketConnection<LocalConnectionContext> {
            private val connectionJob = Job(parentContext[Job])
            override val coroutineContext: CoroutineContext = parentContext + connectionJob
            private val streamsContext = parentContext.supervisorContext()

            override suspend fun sendFrame(frame: Buffer) {
                outgoingFrames.send(frame)
            }

            override suspend fun createStream(): RSocketStreamOutbound {
                val frames = Frames()
                outgoingStreams.send(frames)
                return StreamOutbound(
                    parentContext = streamsContext,
                    streamId = 1, // TODO
                    incoming = frames.clientToServer,
                    outgoing = frames.serverToClient
                )
            }

            override fun close(cause: Throwable?) {
                connectionJob.cancel("Connection closed", cause)
            }

            override fun startReceiving(inbound: RSocketConnectionInbound) {
                launch {
                    try {
                        incomingFrames.consumeEach(inbound::onFrame)
                        inbound.onClose(null)
                    } catch (cause: Throwable) {
                        inbound.onClose(cause)
                        throw cause
                    }
                }
                launch {
                    incomingStreams.consumeEach {
                        // TODO: validate first frame
                        val firstFrame = it.serverToClient.receive()

                        inbound.onStream(
                            firstFrame,
                            StreamOutbound(
                                parentContext = streamsContext,
                                streamId = 1, // TODO
                                incoming = it.serverToClient,
                                outgoing = it.clientToServer
                            )
                        )
                    }
                }
            }
        }

        @RSocketTransportApi
        private class StreamOutbound(
            parentContext: CoroutineContext,
            override val streamId: Int,
            private val incoming: ReceiveChannel<Buffer>,
            private val outgoing: SendChannel<Buffer>,
        ) : RSocketStreamOutbound, CoroutineScope {
            private val streamJob = Job(parentContext[Job])
            override val coroutineContext: CoroutineContext = parentContext + streamJob

            @OptIn(DelicateCoroutinesApi::class)
            override val isClosedForSend: Boolean get() = outgoing.isClosedForSend

            override suspend fun sendFrame(frame: Buffer) {
                return outgoing.send(frame)
            }

            override fun close(cause: Throwable?) {
                streamJob.cancel("Stream closed", cause)
            }

            override fun startReceiving(inbound: RSocketStreamInbound) {
                launch {
                    try {
                        incoming.consumeEach(inbound::onFrame)
                        inbound.onClose(null)
                    } catch (cause: Throwable) {
                        inbound.onClose(cause)
                        throw cause
                    }
                }
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