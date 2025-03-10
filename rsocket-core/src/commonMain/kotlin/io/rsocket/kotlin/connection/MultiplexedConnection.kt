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
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.io.*

@RSocketLoggingApi
@RSocketTransportApi
internal class MultiplexedConnection(
    isClient: Boolean,
    frameCodec: FrameCodec,
    private val frameLogger: Logger,
    private val connection: RSocketMultiplexedConnection,
    private val initialStream: RSocketMultiplexedConnection.Stream,
    private val requestsScope: CoroutineScope,
) : ConnectionOutbound(frameCodec) {
    private val storage = StreamDataStorage<Unit>(isClient)

    init {
        connection.coroutineContext.job.invokeOnCompletion {
            storage.clear()
        }
    }

    override suspend fun sendConnectionFrameRaw(frame: Buffer): Unit = initialStream.sendFrame(frame)
    override suspend fun receiveConnectionFrameRaw(): Buffer? = initialStream.receiveFrame()

    override suspend fun handleConnection(inbound: ConnectionInbound): Unit = coroutineScope {
        launch {
            while (true) {
                val frame = frameCodec.decodeFrame(
                    expectedStreamId = 0,
                    frame = initialStream.receiveFrame() ?: break
                )
                inbound.handleFrame(frame)
            }
        }

        while (true) if (!acceptRequest(inbound)) break
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun launchRequest(
        requestPayload: Payload,
        operation: RequesterOperation,
    ): Job = requestsScope.launch(start = CoroutineStart.ATOMIC) {
        operation.handleExecutionFailure(requestPayload) {
            ensureActive() // because of atomic start
            val stream = connection.createStream()
            val streamId = storage.createStream(Unit)
            try {
                execute(streamId, stream, requestPayload, operation)
            } finally {
                storage.removeStream(streamId)
                stream.cancel("Stream closed")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun acceptRequest(
        connectionInbound: ConnectionInbound,
        stream: RSocketMultiplexedConnection.Stream,
    ): Job = requestsScope.launch(start = CoroutineStart.ATOMIC) {
        try {
            ensureActive() // because of atomic start
            val (
                streamId,
                type,
                initialRequest,
                requestPayload,
                complete,
            ) = receiveRequest(stream)
            try {
                val operation = connectionInbound.createOperation(type, coroutineContext.job)
                operation.handleExecutionFailure(requestPayload) {
                    if (operation.shouldReceiveFrame(FrameType.RequestN))
                        operation.receiveRequestNFrame(initialRequest)
                    if (operation.shouldReceiveFrame(FrameType.Payload) && complete)
                        operation.receivePayloadFrame(null, true)
                    execute(streamId, stream, requestPayload, operation)
                }
            } finally {
                storage.removeStream(streamId)
            }
        } finally {
            stream.cancel("Stream closed")
        }
    }

    private suspend fun acceptRequest(inbound: ConnectionInbound): Boolean {
        val stream = connection.acceptStream() ?: return false
        acceptRequest(inbound, stream)
        return true
    }

    private suspend fun receiveRequest(stream: RSocketMultiplexedConnection.Stream): ResponderOperationData {
        val initialFrame = frameCodec.decodeFrame(
            frame = stream.receiveFrame() ?: error("Expected initial frame for stream")
        )
        val streamId = initialFrame.streamId

        if (streamId == 0) {
            initialFrame.close()
            error("expected stream id != 0")
        }
        if (initialFrame !is RequestFrame || !initialFrame.type.isRequestType) {
            initialFrame.close()
            error("expected request frame type")
        }
        if (!storage.acceptStream(streamId, Unit)) {
            initialFrame.close()
            error("invalid stream id")
        }

        val complete: Boolean
        val requestPayload: Payload
        val assembler = PayloadAssembler()
        try {
            if (initialFrame.follows) {
                assembler.appendFragment(initialFrame.payload)
                while (true) {
                    val frame = frameCodec.decodeFrame(
                        expectedStreamId = streamId,
                        frame = stream.receiveFrame() ?: error("Unexpected stream closure")
                    )
                    when (frame) {
                        // request is cancelled during fragmentation
                        is CancelFrame  -> error("Request was cancelled by remote party")
                        is RequestFrame -> {
                            // TODO[fragmentation]: extract assembly logic?
                            when {
                                // for RC, it could contain the complete flag
                                // complete+follows=complete, "complete" overrides "follows" flag
                                frame.complete               -> check(frame.next) { "next flag should be set" }
                                frame.next && !frame.follows -> {} // last fragment
                                else                         -> {
                                    assembler.appendFragment(frame.payload)
                                    continue // await more fragments
                                }
                            }
                            complete = frame.complete
                            requestPayload = assembler.assemblePayload(frame.payload)
                            break
                        }

                        else            -> {
                            frame.close()
                            error("unexpected frame: ${frame.type}")
                        }
                    }
                }
            } else {
                complete = initialFrame.complete
                requestPayload = initialFrame.payload
            }
        } catch (cause: Throwable) {
            assembler.close()
            throw cause
        }

        return ResponderOperationData(
            streamId = streamId,
            requestType = initialFrame.type,
            initialRequest = initialFrame.initialRequest,
            requestPayload = requestPayload,
            complete = complete
        )
    }

    private suspend fun execute(
        streamId: Int,
        stream: RSocketMultiplexedConnection.Stream,
        requestPayload: Payload,
        operation: Operation,
    ): Unit = coroutineScope {
        val outbound = Outbound(streamId, stream)
        val receiveJob = launch {
            val handler = OperationFrameHandler(operation, frameLogger)
            try {
                while (true) {
                    val frame = frameCodec.decodeFrame(
                        expectedStreamId = streamId,
                        frame = stream.receiveFrame() ?: break
                    )
                    handler.handleFrame(frame)
                }
                handler.handleDone()
            } finally {
                handler.close()
            }
        }
        operation.execute(outbound, requestPayload)
        receiveJob.cancel() // stop receiving
    }

    private inner class Outbound(
        streamId: Int,
        private val stream: RSocketMultiplexedConnection.Stream,
    ) : OperationOutbound(streamId, frameCodec) {
        override suspend fun sendFrame(frame: Buffer): Unit = stream.sendFrame(frame)
    }

}
