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
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

internal class RequesterRequestChannelOperation(
    private val requestNs: ReceiveChannel<Int>,
    private val responsePayloads: SendChannel<Payload>,
    private val requestPayloads: Flow<Payload>,
) : RequesterOperation() {
    override val type: RSocketOperationType get() = RSocketOperationType.RequestChannel

    private val limiter = Limiter(0)
    private var senderJob: Job? = null
    override var needCancelling = true
        private set

    override suspend fun execute(outbound: OperationOutbound): Unit = coroutineScope {
        senderJob = launch {
            try {
                requestPayloads.collectLimiting(limiter) { payload ->
                    outbound.sendNext(payload, complete = false)
                }
                outbound.sendComplete()
            } catch (cause: Throwable) {
                // senderJob could be cancelled
                if (isActive) {
                    outbound.sendError(cause)
                    needCancelling = false
                }
                throw cause // failing senderJob here will fail request
            }
        }
        while (true) {
            outbound.sendRequestN(requestNs.receiveCatching().getOrNull() ?: break)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun isFrameExpected(frameType: FrameType): Boolean = when {
        !responsePayloads.isClosedForSend -> frameType == FrameType.Payload || frameType == FrameType.Error
        else                              -> false
    } || when {
        senderJob?.isActive == true -> frameType == FrameType.RequestN || frameType == FrameType.Cancel
        else                        -> false
    }

    override fun receiveRequestN(requestN: Int) {
        limiter.updateRequests(requestN)
    }

    override fun receiveNext(payload: Payload?, complete: Boolean) {
        if (payload != null) {
            if (responsePayloads.trySend(payload).isFailure) payload.close()
        }
        if (complete) closeChannels(null)
    }

    override fun receiveCancel() {
        senderJob?.cancel("Request payloads cancelled")
    }

    override fun receiveError(cause: Throwable) {
        closeChannels(cause)
        senderJob?.cancel("Error received from remote", cause)
    }

    override fun receiveProcessingError(cause: Throwable) {
        closeChannels(cause)
        senderJob?.cancel("Request processing failure", cause)
    }

    private fun closeChannels(cause: Throwable?) {
        responsePayloads.close(cause)
        requestNs.cancel() // no more requestN can be sent
    }
}
