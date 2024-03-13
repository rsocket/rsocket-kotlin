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

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*

@OptIn(RSocketTransportApi::class)
internal suspend fun RSocketTransportSession.Multiplexed.establishConnection(
    maxFragmentSize: Int,
    bufferPool: BufferPool,
    handler: ConnectionEstablishmentHandler,
): InternalConnection {
    val connectionStream = when {
        handler.isClient -> createStream()
        else             -> awaitStream()
    }
    val context = MultiplexedConnectionEstablishmentContext(bufferPool, connectionStream)
    val config = handler.establishConnection(context)
    return MultiplexedConnection(handler.isClient, maxFragmentSize, bufferPool, config, connectionStream, this)
}

@OptIn(RSocketTransportApi::class)
private class MultiplexedConnectionEstablishmentContext(
    bufferPool: BufferPool,
    private val connectionStream: RSocketTransportSession.Multiplexed.Stream,
) : AbstractConnectionEstablishmentContext(bufferPool) {
    override suspend fun sendFrame(frame: ByteReadPacket): Unit = connectionStream.sendFrame(frame)
    override suspend fun receiveFrameRaw(): ByteReadPacket = connectionStream.receiveFrame()
}

@OptIn(RSocketTransportApi::class)
private class MultiplexedConnection(
    isClient: Boolean,
    private val maxFragmentSize: Int,
    private val bufferPool: BufferPool,
    override val config: ConnectionConfig,
    private val connectionStream: RSocketTransportSession.Multiplexed.Stream,
    private val session: RSocketTransportSession.Multiplexed,
) : InternalConnection {
    private val storage = OperationStateStorage<Unit>(isClient)

    override val outbound: ConnectionOutbound =
        MultiplexedConnectionOutbound(bufferPool, connectionStream)

    override val requesterOperationFactory: RequesterOperationFactory =
        MultiplexedRequesterOperationFactory(maxFragmentSize, bufferPool, storage, session)

    init {
        session.coroutineContext.job.invokeOnCompletion { _ ->
            config.setupPayload.close()
            storage.clear()
        }
    }

    override fun start(connectionFrameHandler: ConnectionFrameHandler, responderWrapper: ResponderWrapper) {
        session.launch {
            while (true) {
                val frame = connectionStream.receiveFrame().readFrame(bufferPool)
                check(frame.streamId == 0) { "expected 0 frame" }
                connectionFrameHandler.handleFrame(frame) // TODO: handle
            }
        }

        session.launch {
            while (true) {
                val stream = session.awaitStream()
                stream.launch {
                    receiveStream(stream, responderWrapper)
                }
            }
        }
    }

    private suspend fun receiveStream(
        stream: RSocketTransportSession.Multiplexed.Stream,
        responderWrapper: ResponderWrapper,
    ) {
        val requestFrame = stream.receiveFrame().readFrame(bufferPool)
        val streamId = requestFrame.streamId
        check(streamId != 0) { "expected stream id != 0" }
        check(requestFrame.type.isRequestType) { "expected request type" }

        if (!storage.isValidForAccept(streamId)) error("invalid stream id")

        val outbound = MultiplexedOperationOutbound(streamId, maxFragmentSize, bufferPool, storage, stream)
        val frameHandler = responderWrapper.operationFrameHandler(requestFrame.type, outbound)
        if (!storage.acceptStream(streamId, Unit)) error("invalid stream id")

        frameHandler.handleFrame(requestFrame)
        outbound.receiveFrames(frameHandler)
    }
}


@OptIn(RSocketTransportApi::class)
private class MultiplexedConnectionOutbound(
    bufferPool: BufferPool,
    private val connectionStream: RSocketTransportSession.Multiplexed.Stream,
) : AbstractConnectionOutbound(bufferPool) {
    override suspend fun sendFrame(frame: ByteReadPacket): Unit = connectionStream.sendFrame(frame)
}

@OptIn(RSocketTransportApi::class)
private class MultiplexedOperationOutbound(
    streamId: Int,
    maxFragmentSize: Int,
    bufferPool: BufferPool,
    storage: OperationStateStorage<Unit>,
    private val stream: RSocketTransportSession.Multiplexed.Stream,
) : AbstractOperationOutbound(streamId, maxFragmentSize, bufferPool) {
    init {
        stream.coroutineContext.job.invokeOnCompletion { storage.removeStream(streamId) }
    }

    override suspend fun sendFrame(frame: ByteReadPacket): Unit = stream.sendFrame(frame)
    override fun close(): Unit = stream.cancel("Stream is closed")

    suspend fun receiveFrames(handler: OperationFrameHandler) {
        while (true) {
            val frame = stream.receiveFrame().readFrame(bufferPool)
            check(frame.streamId == streamId) { "wrong stream id" }
            handler.handleFrame(frame)
        }
    }
}

@OptIn(RSocketTransportApi::class)
private class MultiplexedRequesterOperationFactory(
    private val maxFragmentSize: Int,
    private val bufferPool: BufferPool,
    private val storage: OperationStateStorage<Unit>,
    private val session: RSocketTransportSession.Multiplexed,
) : RequesterOperationFactory {
    override suspend fun createRequest(type: RSocketOperationType, handler: OperationFrameHandler): OperationOutbound {
        val stream = session.createStream()
        val outbound = MultiplexedOperationOutbound(
            streamId = storage.createStream(Unit),
            maxFragmentSize = maxFragmentSize,
            bufferPool = bufferPool,
            storage = storage,
            stream = stream
        )
        stream.launch { outbound.receiveFrames(handler) }
        return outbound
    }
}
