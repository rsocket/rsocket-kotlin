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
internal suspend fun RSocketTransportSession.Sequential.establishConnection(
    maxFragmentSize: Int,
    bufferPool: BufferPool,
    handler: ConnectionEstablishmentHandler,
): InternalConnection {
    val context = SequentialConnectionEstablishmentContext(bufferPool, this)
    val config = handler.establishConnection(context)
    return SequentialConnection(handler.isClient, maxFragmentSize, bufferPool, config, this)
}

@OptIn(RSocketTransportApi::class)
private class SequentialConnectionEstablishmentContext(
    bufferPool: BufferPool,
    private val session: RSocketTransportSession.Sequential,
) : AbstractConnectionEstablishmentContext(bufferPool) {
    override suspend fun sendFrame(frame: ByteReadPacket): Unit = session.sendFrame(frame)
    override suspend fun receiveFrameRaw(): ByteReadPacket = session.receiveFrame()
}

@OptIn(RSocketTransportApi::class)
private class SequentialConnection(
    private val isClient: Boolean,
    private val maxFragmentSize: Int,
    private val bufferPool: BufferPool,
    override val config: ConnectionConfig,
    private val session: RSocketTransportSession.Sequential,
) : InternalConnection {
    private val prioritizer = SequentialFramePrioritizer()
    private val storage = OperationStateStorage<OperationFrameHandler>(isClient)

    override val outbound: ConnectionOutbound =
        SequentialConnectionOutbound(bufferPool, prioritizer)

    override val requesterOperationFactory: RequesterOperationFactory =
        SequentialRequesterOperationFactory(maxFragmentSize, bufferPool, storage, prioritizer)

    init {
        session.coroutineContext.job.invokeOnCompletion { cause ->
            config.setupPayload.close()
            storage.clear().forEach(OperationFrameHandler::close)
            prioritizer.close(cause)
        }
    }

    override fun start(connectionFrameHandler: ConnectionFrameHandler, responderWrapper: ResponderWrapper) {
        session.launch {
            while (true) {
                session.sendFrame(prioritizer.receive())
            }
        }

        session.launch {
            while (true) {
                val frame = session.receiveFrame().readFrame(bufferPool)
                when (val streamId = frame.streamId) {
                    0    -> connectionFrameHandler.handleFrame(frame)
                    else -> handleOperationFrame(streamId, frame, responderWrapper)
                }
            }
        }
    }

    private fun handleOperationFrame(
        streamId: Int,
        frame: Frame,
        responderWrapper: ResponderWrapper,
    ) {
        fun getHandler(): OperationFrameHandler? {
            if (!frame.type.isRequestType) return storage.getStream(streamId)

            if (!storage.isValidForAccept(streamId)) return null

            val outbound = SequentialOperationOutbound(streamId, maxFragmentSize, bufferPool, storage, prioritizer)

            val frameHandler = responderWrapper.operationFrameHandler(frame.type, outbound)

            if (!storage.acceptStream(streamId, frameHandler)) return null

            return frameHandler
        }

        getHandler()?.handleFrame(frame, isClient) ?: return frame.close()
    }
}

private class SequentialConnectionOutbound(
    bufferPool: BufferPool,
    private val prioritizer: SequentialFramePrioritizer,
) : AbstractConnectionOutbound(bufferPool) {
    override suspend fun sendFrame(frame: ByteReadPacket): Unit = prioritizer.sendPriority(frame)
}

private class SequentialOperationOutbound(
    streamId: Int,
    maxFragmentSize: Int,
    bufferPool: BufferPool,
    private val storage: OperationStateStorage<OperationFrameHandler>,
    private val prioritizer: SequentialFramePrioritizer,
) : AbstractOperationOutbound(streamId, maxFragmentSize, bufferPool) {
    override suspend fun sendFrame(frame: ByteReadPacket): Unit = prioritizer.sendCommon(frame)
    override fun close() {
        storage.removeStream(streamId)?.close()
    }
}

private class SequentialRequesterOperationFactory(
    private val maxFragmentSize: Int,
    private val bufferPool: BufferPool,
    private val storage: OperationStateStorage<OperationFrameHandler>,
    private val prioritizer: SequentialFramePrioritizer,
) : RequesterOperationFactory {
    override suspend fun createRequest(type: RSocketOperationType, handler: OperationFrameHandler): OperationOutbound {
        val streamId = storage.createStream(handler)
        return SequentialOperationOutbound(streamId, maxFragmentSize, bufferPool, storage, prioritizer)
    }
}
