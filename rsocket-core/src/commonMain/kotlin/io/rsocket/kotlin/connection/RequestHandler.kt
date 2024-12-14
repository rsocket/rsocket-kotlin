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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.io.*

@RSocketTransportApi
internal class RequestHandler(
    private val requestScope: CoroutineScope,
    private val frameCodec: FrameCodec,
    private val responder: RSocket,
) {
    fun handleRequest(frame: Buffer, stream: RSocketStreamOutbound) {
        val initialFrame = frameCodec.decodeFrame(stream.streamId, frame)
        if (initialFrame !is RequestFrame || !initialFrame.type.isRequestType) {
            println("unexpected initial frame: $initialFrame")
            stream.close(null)
            return
        }

        val operation = when (initialFrame.type) {
            FrameType.RequestFnF      -> ResponderFireAndForgetOperation(responder)
            FrameType.RequestResponse -> ResponderRequestResponseOperation(responder)
            FrameType.RequestStream   -> ResponderRequestStreamOperation(responder)
            FrameType.RequestChannel  -> ResponderRequestChannelOperation(responder)
            else                      -> error("should not happen")
        }

        val inbound = ResponderRequestInbound(operation, stream, initialFrame.initialRequest)
        stream.startReceiving(OperationFrameHandler(stream.streamId, inbound, frameCodec, initialFrame))
    }

    private inner class ResponderRequestInbound(
        private val operation: ResponderOperation,
        private val stream: RSocketStreamOutbound,
        private val initialRequest: Int,
    ) : OperationInbound {
        private var requestJob: Job? = null

        override fun shouldReceiveFrame(frameType: FrameType): Boolean {
            val requestJob = requestJob

            return when {
                requestJob == null  -> frameType === FrameType.Cancel || frameType.isRequestType || frameType === FrameType.Payload
                requestJob.isActive -> frameType === FrameType.Cancel || operation.shouldReceiveFrame(frameType)
                else                -> false
            }
        }

        override fun receivePayloadFrame(payload: Payload?, complete: Boolean) {
            when (requestJob) {
                null -> {
                    require(payload != null) { "should never happen" }
                    requestJob = requestScope.launch {
                        if (initialRequest != 0 && operation.shouldReceiveFrame(FrameType.RequestN))
                            operation.receiveRequestNFrame(initialRequest)
                        if (complete && operation.shouldReceiveFrame(FrameType.Payload))
                            operation.receivePayloadFrame(null, true)

                        try {
                            operation.execute(OperationOutbound(stream, frameCodec), payload)
                            stream.close(null)
                        } catch (cause: Throwable) {
                            stream.close(cause)
                            throw cause
                        }
                    }
                }

                else -> operation.receivePayloadFrame(payload, complete)
            }
        }

        override fun receiveRequestNFrame(requestN: Int) {
            operation.receiveRequestNFrame(requestN)
        }

        override fun receiveErrorFrame(cause: Throwable) {
            operation.receiveErrorFrame(cause)
        }

        override fun receiveCancelFrame() {
            when (val requestJob = requestJob) {
                null -> stream.close(null)
                else -> requestJob.cancel("Request cancelled")
            }
        }

        override fun receiveDone() {
            // TODO
            operation.receiveDone()
        }
    }
}