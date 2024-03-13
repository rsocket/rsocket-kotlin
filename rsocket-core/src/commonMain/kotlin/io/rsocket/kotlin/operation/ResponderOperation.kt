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

package io.rsocket.kotlin.operation

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal abstract class ResponderOperation : OperationInbound {
    abstract val type: RSocketOperationType

    // after `execute` is completed, no other interactions with the operation are possible
    abstract suspend fun execute(outbound: OperationOutbound, payload: Payload, complete: Boolean)

    // handled outside in WrappedResponderOperation
    final override fun receiveCancel() {}
    final override fun receiveProcessingError(cause: Throwable) {}
}

internal class ResponderWrapper(
    private val requestsScope: CoroutineScope,
    private val responder: RSocket,
) {
    fun operationFrameHandler(type: FrameType, outbound: OperationOutbound): OperationFrameHandler =
        OperationFrameHandler(
            WrappedResponderOperation(
                requestsScope = requestsScope,
                operation = when (type) {
                    FrameType.RequestFnF      -> ResponderFireAndForgetOperation(responder)
                    FrameType.RequestResponse -> ResponderRequestResponseOperation(responder)
                    FrameType.RequestStream   -> ResponderRequestStreamOperation(responder)
                    FrameType.RequestChannel  -> ResponderRequestChannelOperation(responder)
                    else                      -> error("should not happen")
                },
                outbound = outbound
            )
        )
}

private class WrappedResponderOperation(
    private val requestsScope: CoroutineScope,
    private val operation: ResponderOperation,
    private val outbound: OperationOutbound,
) : OperationInbound {

    private var requestJob: Job? = null

    override fun isFrameExpected(frameType: FrameType): Boolean {
        val requestJob = requestJob
        return when {
            requestJob == null  -> frameType.isRequestType || frameType == FrameType.Payload || frameType == FrameType.Cancel
            requestJob.isActive -> frameType == FrameType.Cancel || operation.isFrameExpected(frameType)
            else                -> false
        }
    }

    override fun receiveRequestN(requestN: Int): Unit = operation.receiveRequestN(requestN)
    override fun receiveError(cause: Throwable): Unit = operation.receiveError(cause)
    override fun receiveNext(payload: Payload?, complete: Boolean) {
        when (requestJob) {
            null -> {
                // TODO: need to somehow enforce this better
                checkNotNull(payload) { "Payload should be present for request" }
                @OptIn(ExperimentalCoroutinesApi::class)
                requestJob = requestsScope.launch(start = CoroutineStart.ATOMIC) {
                    try {
                        ensureActive()
                    } catch (cause: Throwable) {
                        payload.close()
                        throw cause
                    }
                    try {
                        operation.execute(outbound, payload, complete)
                    } catch (cause: Throwable) {
                        // if scope is not active, this means that request was cancelled
                        // `Error` frame is not needed FaF
                        if (isActive && operation.type != RSocketOperationType.FireAndForget) {
                            outbound.sendError(cause)
                        }
                        throw cause
                    } finally {
                        outbound.close()
                    }
                }
            }

            else -> operation.receiveNext(payload, complete)
        }
    }

    override fun receiveCancel() {
        cancelRequest("Request cancelled", null)
    }

    override fun receiveProcessingError(cause: Throwable) {
        cancelRequest("Request processing failure", cause)
    }

    private fun cancelRequest(message: String, cause: Throwable?) {
        when (val requestJob = requestJob) {
            null -> outbound.close()
            else -> requestJob.cancel(message, cause) // will cause outbound closing
        }
    }
}
