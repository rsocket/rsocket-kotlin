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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*

internal sealed class LocalServerConnector {
    @RSocketTransportApi
    abstract suspend fun connect(
        serverInbound: RSocketServerInstance.Inbound<LocalConnectionContext>,
        clientScope: CoroutineScope,
        serverScope: CoroutineScope,
    ): RSocketConnection<LocalConnectionContext>

    object Sequential : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            serverInbound: RSocketServerInstance.Inbound<LocalConnectionContext>,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketConnection<LocalConnectionContext> = Multiplexed.connect(serverInbound, clientScope, serverScope)
    }

    object Multiplexed : LocalServerConnector() {
        @RSocketTransportApi
        override suspend fun connect(
            serverInbound: RSocketServerInstance.Inbound<LocalConnectionContext>,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketConnection<LocalConnectionContext> {
            val zeroStream = Stream()
            val streams = Streams()

            serverInbound.onConnection(
                ConnectionOutbound(
                    parentContext = serverScope.coroutineContext,
                    incomingFrames = zeroStream.clientToServer,
                    outgoingFrames = zeroStream.serverToClient,
                    incomingStreams = streams.clientToServer,
                    outgoingStreams = streams.serverToClient
                )
            )

            return ConnectionOutbound(
                parentContext = clientScope.coroutineContext,
                incomingFrames = zeroStream.serverToClient,
                outgoingFrames = zeroStream.clientToServer,
                incomingStreams = streams.serverToClient,
                outgoingStreams = streams.clientToServer
            )
        }

        @RSocketTransportApi
        private class ConnectionOutbound(
            parentContext: CoroutineContext,
            private val incomingFrames: ReceiveChannel<Buffer>,
            private val outgoingFrames: SendChannel<Buffer>,
            private val incomingStreams: ReceiveChannel<Stream>,
            private val outgoingStreams: SendChannel<Stream>,
        ) : MultiplexedRSocketConnection<LocalConnectionContext> {
            private val connectionJob = Job(parentContext[Job])
            override val coroutineContext: CoroutineContext = parentContext + connectionJob
            private val streamsContext = parentContext.supervisorContext()

            override suspend fun sendFrame(frame: Buffer) {
                outgoingFrames.send(frame)
            }

            override suspend fun createStream(): RSocketStreamOutbound {
                val stream = Stream()
                outgoingStreams.send(stream)
                return StreamOutbound(
                    parentContext = streamsContext,
                    streamId = 1, // TODO
                    incoming = stream.clientToServer,
                    outgoing = stream.serverToClient
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

        private class Streams : AutoCloseable {
            val clientToServer = channelForCloseable<Stream>(Channel.BUFFERED)
            val serverToClient = channelForCloseable<Stream>(Channel.BUFFERED)

            // only for undelivered element case
            override fun close() {
                clientToServer.cancel()
                serverToClient.cancel()
            }
        }

        private class Stream : AutoCloseable {
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
