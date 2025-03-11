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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.io.*

@RSocketLoggingApi
@RSocketTransportApi
internal class SequentialConnection(
    isClient: Boolean,
    frameCodec: FrameCodec,
    private val frameLogger: Logger,
    private val connection: RSocketSequentialConnection,
    private val requestsScope: CoroutineScope,
) : ConnectionOutbound(frameCodec) {
    private val storage = StreamDataStorage<OperationFrameHandler>(isClient)

    init {
        connection.coroutineContext.job.invokeOnCompletion {
            storage.clear().forEach { it.close() }
        }
    }

    override suspend fun sendConnectionFrameRaw(frame: Buffer): Unit = connection.sendFrame(streamId = 0, frame)
    override suspend fun receiveConnectionFrameRaw(): Buffer? = connection.receiveFrame()

    override suspend fun handleConnection(inbound: ConnectionInbound) {
        while (true) {
            val frame = frameCodec.decodeFrame(
                frame = connection.receiveFrame() ?: break
            )
            when (frame.streamId) {
                0    -> inbound.handleFrame(frame)
                else -> receiveFrame(inbound, frame)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun launchRequest(
        requestPayload: Payload,
        operation: RequesterOperation,
    ): Job = requestsScope.launch(start = CoroutineStart.ATOMIC) {
        operation.handleExecutionFailure(requestPayload) {
            ensureActive() // because of atomic start
            val streamId = storage.createStream(OperationFrameHandler(operation, frameLogger))
            try {
                operation.execute(Outbound(streamId), requestPayload)
            } finally {
                storage.removeStream(streamId)?.close()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun acceptRequest(
        connectionInbound: ConnectionInbound,
        operationData: ResponderOperationData,
    ): ResponderOperation {
        val requestJob = Job(requestsScope.coroutineContext.job)
        val operation = connectionInbound.createOperation(operationData.requestType, requestJob)
        requestsScope.launch(requestJob, start = CoroutineStart.ATOMIC) {
            val (
                streamId,
                _,
                initialRequest,
                requestPayload,
                complete,
            ) = operationData
            operation.handleExecutionFailure(requestPayload) {
                ensureActive() // because of atomic start
                try {
                    if (operation.shouldReceiveFrame(FrameType.RequestN))
                        operation.receiveRequestNFrame(initialRequest)
                    if (operation.shouldReceiveFrame(FrameType.Payload) && complete)
                        operation.receivePayloadFrame(null, true)
                    operation.execute(Outbound(streamId), requestPayload)
                } finally {
                    storage.removeStream(streamId)?.close()
                }
            }
        }
        requestJob.complete()
        return operation
    }

    private fun receiveFrame(connectionInbound: ConnectionInbound, frame: Frame) {
        val streamId = frame.streamId
        if (frame is RequestFrame && frame.type.isRequestType) {
            if (storage.isValidForAccept(streamId)) {
                val operationData = ResponderOperationData(
                    streamId = streamId,
                    requestType = frame.type,
                    initialRequest = frame.initialRequest,
                    requestPayload = frame.payload,
                    complete = frame.complete
                )
                val handler = OperationFrameHandler(
                    when {
                        frame.follows -> ResponderInboundWrapper(connectionInbound, operationData)
                        else          -> acceptRequest(connectionInbound, operationData)
                    },
                    frameLogger
                )
                if (storage.acceptStream(streamId, handler)) {
                    // for fragmentation
                    if (frame.follows) handler.handleFrame(frame)
                } else {
                    frame.close()
                    handler.close()
                }
            } else {
                frame.close() // ignore
            }
        } else {
            storage.getStream(streamId)?.handleFrame(frame) ?: frame.close()
        }
    }

    private inner class Outbound(streamId: Int) : OperationOutbound(streamId, frameCodec) {
        override suspend fun sendFrame(frame: Buffer): Unit = connection.sendFrame(streamId, frame)
    }

    private inner class ResponderInboundWrapper(
        private val connectionInbound: ConnectionInbound,
        private val operationData: ResponderOperationData,
    ) : OperationInbound {

        override fun shouldReceiveFrame(frameType: FrameType): Boolean {
            return frameType.isRequestType || frameType === FrameType.Payload || frameType === FrameType.Cancel
        }

        override fun receivePayloadFrame(payload: Payload?, complete: Boolean) {
            if (payload != null) {
                val operation = acceptRequest(
                    connectionInbound = connectionInbound,
                    operationData = ResponderOperationData(
                        streamId = operationData.streamId,
                        requestType = operationData.requestType,
                        initialRequest = operationData.initialRequest,
                        requestPayload = payload,
                        complete = complete
                    )
                )
                // close old handler
                storage.replaceStream(operationData.streamId, OperationFrameHandler(operation, frameLogger))?.close()
            } else {
                // should not happen really
                storage.removeStream(operationData.streamId)?.close()
            }
        }

        override fun receiveCancelFrame() {
            storage.removeStream(operationData.streamId)?.close()
        }

        override fun receiveDone() {
            // if for some reason it happened...
            storage.removeStream(operationData.streamId)?.close()
        }
    }
}
