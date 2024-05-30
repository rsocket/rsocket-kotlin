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

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class RequesterRequestChannelOperation(
    private val initialRequestN: Int,
    private val requestPayloads: Flow<Payload>,
    private val responsePayloads: PayloadChannel,
) : RequesterOperation {
    private val limiter = PayloadLimiter(0)
    private var senderJob: Job? by atomic(null)
    private var failure: Throwable? = null

    override suspend fun execute(outbound: OperationOutbound, requestPayload: Payload) {
        try {
            coroutineScope {
                outbound.sendRequest(
                    type = FrameType.RequestChannel,
                    payload = requestPayload,
                    complete = false,
                    initialRequest = initialRequestN
                )

                senderJob = launch {
                    try {
                        requestPayloads.collectLimiting(limiter) { payload ->
                            outbound.sendNext(payload, complete = false)
                        }
                        outbound.sendComplete()
                    } catch (cause: Throwable) {
                        // senderJob could be cancelled
                        if (isActive) failure = cause
                        throw cause // failing senderJob here will fail request
                    }
                }

                try {
                    while (true) outbound.sendRequestN(responsePayloads.nextRequestN() ?: break)
                } catch (cause: Throwable) {
                    if (!currentCoroutineContext().isActive || !outbound.isClosed) throw cause
                }
            }
        } catch (cause: Throwable) {
            if (!outbound.isClosed) withContext(NonCancellable) {
                when (val error = failure) {
                    null -> outbound.sendCancel()
                    else -> outbound.sendError(error)
                }
            }
            throw cause
        }
    }

    override fun shouldReceiveFrame(frameType: FrameType): Boolean = when {
        responsePayloads.isActive -> frameType == FrameType.Payload || frameType == FrameType.Error
        else                      -> false
    } || when {
        // TODO: handle cancel, when `senderJob` is not started
        senderJob == null || senderJob?.isActive == true -> frameType == FrameType.RequestN || frameType == FrameType.Cancel
        else                                             -> false
    }

    override fun receiveRequestNFrame(requestN: Int) {
        limiter.updateRequests(requestN)
    }

    override fun receivePayloadFrame(payload: Payload?, complete: Boolean) {
        if (payload != null) responsePayloads.trySend(payload)
        if (complete) responsePayloads.close(null)
    }

    override fun receiveCancelFrame() {
        senderJob?.cancel("Request payloads cancelled")
    }

    override fun receiveErrorFrame(cause: Throwable) {
        responsePayloads.close(cause)
        senderJob?.cancel("Error received from remote", cause)
    }

    override fun receiveDone() {
        if (responsePayloads.isActive) responsePayloads.close(
            IllegalStateException("Unexpected end of stream")
        )
    }

    override fun operationFailure(cause: Throwable) {
        if (responsePayloads.isActive) responsePayloads.close(cause)
    }
}
