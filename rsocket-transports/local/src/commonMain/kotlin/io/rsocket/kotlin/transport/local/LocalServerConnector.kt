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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal sealed class LocalServerConnector {
    @RSocketTransportApi
    abstract fun connect(
        acceptor: RSocketServerAcceptor,
        clientScope: CoroutineScope,
        serverScope: CoroutineScope,
    ): RSocketTransportSession

    class Sequential(
        private val connectionBufferCapacity: Int,
    ) : LocalServerConnector() {
        @RSocketTransportApi
        override fun connect(
            acceptor: RSocketServerAcceptor,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketTransportSession {
            clientScope.ensureActive()
            serverScope.ensureActive()

            val clientToServer = channelForCloseable<ByteReadPacket>(connectionBufferCapacity)
            val serverToClient = channelForCloseable<ByteReadPacket>(connectionBufferCapacity)

            serverScope.launch {
                acceptor.acceptSession(
                    Session(
                        coroutineContext = coroutineContext.childContext(),
                        outbound = serverToClient,
                        inbound = clientToServer
                    )
                )
            }

            return Session(
                coroutineContext = clientScope.coroutineContext.childContext(),
                outbound = clientToServer,
                inbound = serverToClient
            )
        }


        @RSocketTransportApi
        private class Session(
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

    }

    class Multiplexed(
        private val connectionStreamsQueueCapacity: Int,
        private val streamBufferCapacity: Int,
    ) : LocalServerConnector() {
        @RSocketTransportApi
        override fun connect(
            acceptor: RSocketServerAcceptor,
            clientScope: CoroutineScope,
            serverScope: CoroutineScope,
        ): RSocketTransportSession {
            clientScope.ensureActive()
            serverScope.ensureActive()

            val clientToServer = channelForCloseable<Stream>(connectionStreamsQueueCapacity)
            val serverToClient = channelForCloseable<Stream>(connectionStreamsQueueCapacity)

            serverScope.launch {
                acceptor.acceptSession(
                    Session(
                        coroutineContext = coroutineContext.childContext(),
                        outbound = serverToClient,
                        inbound = clientToServer,
                        streamBufferCapacity = streamBufferCapacity
                    )
                )
            }

            return Session(
                coroutineContext = clientScope.coroutineContext.childContext(),
                outbound = clientToServer,
                inbound = serverToClient,
                streamBufferCapacity = streamBufferCapacity
            )
        }

        @RSocketTransportApi
        private class Session(
            override val coroutineContext: CoroutineContext,
            private val outbound: SendChannel<Stream>,
            private val inbound: ReceiveChannel<Stream>,
            private val streamBufferCapacity: Int,
        ) : RSocketTransportSession.Multiplexed {
            private val streamsContext = coroutineContext.supervisorContext()

            init {
                coroutineContext.job.invokeOnCompletion {
                    outbound.close(it)
                    inbound.cancel(CancellationException("Local connection closed", it))
                }
            }

            override suspend fun createStream(): RSocketTransportSession.Multiplexed.Stream {
                val clientToServer = channelForCloseable<ByteReadPacket>(streamBufferCapacity)
                val serverToClient = channelForCloseable<ByteReadPacket>(streamBufferCapacity)

                outbound.send(
                    Stream(
                        coroutineContext = streamsContext.childContext(),
                        outbound = serverToClient,
                        inbound = clientToServer
                    )
                )

                return Stream(
                    coroutineContext = streamsContext.childContext(),
                    outbound = clientToServer,
                    inbound = serverToClient
                )
            }

            override suspend fun awaitStream(): RSocketTransportSession.Multiplexed.Stream {
                return inbound.receive()
            }
        }

        @RSocketTransportApi
        private class Stream(
            override val coroutineContext: CoroutineContext,
            private val outbound: SendChannel<ByteReadPacket>,
            private val inbound: ReceiveChannel<ByteReadPacket>,
        ) : RSocketTransportSession.Multiplexed.Stream, Closeable {

            init {
                coroutineContext.job.invokeOnCompletion {
                    outbound.close(it)
                    inbound.cancel(CancellationException("Local stream closed", it))
                }
            }

            override fun updatePriority(value: Int) {} // no effect

            override suspend fun sendFrame(frame: ByteReadPacket) {
                outbound.send(frame)
            }

            override suspend fun receiveFrame(): ByteReadPacket {
                return inbound.receive()
            }

            override fun close() {
                cancel("Stream is closed") // just for undelivered element case
            }
        }

    }
}
