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

package io.rsocket.kotlin.operation

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal class ResponderRequestChannelOperation(
    private val requestJob: Job,
    private val responder: RSocket,
) : ResponderOperation {
    private val limiter = PayloadLimiter(0)
    private val requestPayloads = PayloadChannel()

    override suspend fun execute(outbound: OperationOutbound, requestPayload: Payload) {
        try {
            coroutineScope {
                @OptIn(ExperimentalStreamsApi::class)
                val requestFlow = payloadFlow { strategy, initialRequest ->
                    // if requestPayloads flow is consumed after the request is completed - we should fail
                    ensureActive()
                    val senderJob = launch {
                        try {
                            outbound.sendRequestN(initialRequest)
                            while (true) outbound.sendRequestN(requestPayloads.nextRequestN() ?: break)
                        } catch (cause: Throwable) {
                            // send cancel only if the operation is active
                            if (this@coroutineScope.isActive) nonCancellable { outbound.sendCancel() }
                            throw cause
                        }
                    }

                    throw try {
                        requestPayloads.consumeInto(this, strategy)
                    } catch (cause: Throwable) {
                        senderJob.cancel()
                        throw cause
                    } ?: return@payloadFlow
                }

                responder.requestChannel(requestPayload, requestFlow).collectLimiting(limiter) { responsePayload ->
                    outbound.sendNext(responsePayload, complete = false)
                }
                outbound.sendComplete()
            }
        } catch (cause: Throwable) {
            requestPayloads.close(cause)
            if (currentCoroutineContext().isActive) outbound.sendError(cause)
            throw cause
        }
    }

    override fun shouldReceiveFrame(frameType: FrameType): Boolean =
        frameType === FrameType.Cancel || when {
            requestPayloads.isActive -> frameType === FrameType.Payload || frameType === FrameType.Error
            else                     -> false
        } || frameType === FrameType.RequestN // TODO

    override fun receiveRequestNFrame(requestN: Int) {
        limiter.updateRequests(requestN)
    }

    override fun receivePayloadFrame(payload: Payload?, complete: Boolean) {
        if (payload != null) requestPayloads.trySend(payload)
        if (complete) requestPayloads.close(null)
    }

    override fun receiveErrorFrame(cause: Throwable) {
        requestPayloads.close(cause)
    }

    override fun receiveCancelFrame() {
        requestJob.cancel("Request was cancelled by remote party")
    }

    override fun operationFailure(cause: Throwable) {
        requestPayloads.close(cause)
    }

    override fun receiveDone() {
        if (requestPayloads.isActive) requestPayloads.close(IllegalStateException("Unexpected end of stream"))
    }
}
