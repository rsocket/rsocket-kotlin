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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class ResponderRequestChannelOperation(
    private val responder: RSocket,
) : ResponderOperation() {
    override val type: RSocketOperationType get() = RSocketOperationType.RequestChannel

    private val limiter = Limiter(0)

    // TODO: should be configurable
    private val requestPayloads = channelForCloseable<Payload>(Channel.UNLIMITED)
    private val requestNs = Channel<Int>(Channel.UNLIMITED)

    @OptIn(ExperimentalStreamsApi::class)
    override suspend fun execute(outbound: OperationOutbound, payload: Payload, complete: Boolean): Unit = coroutineScope {
        val requestFlow = requestFlow { strategy, initialRequest ->
            // if requestPayloads flow is consumed after the request is completed - we should fail
            ensureActive()
            launch {
                outbound.sendRequestN(initialRequest)
                while (true) {
                    outbound.sendRequestN(requestNs.receiveCatching().getOrNull() ?: break)
                }
            }

            val error = try {
                emitAllWithRequestN(requestPayloads, requestNs, strategy)
            } catch (cause: Throwable) {
                if (isActive) launch { outbound.sendCancel() }
                throw cause
            } finally {
                requestPayloads.cancel() // no more payloads can be received
                requestNs.cancel() // no more requestN can be sent
            }
            throw error ?: return@requestFlow
        }

        try {
            payload.use {
                responder.requestChannel(it, requestFlow).collectLimiting(limiter) { responsePayload ->
                    outbound.sendNext(responsePayload, complete = false)
                }
            }
            outbound.sendComplete()
        } catch (cause: Throwable) {
            closeChannels(cause)
            throw cause
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun isFrameExpected(frameType: FrameType): Boolean = when {
        !requestPayloads.isClosedForSend -> frameType == FrameType.Payload || frameType == FrameType.Error
        else                             -> false
    } || frameType == FrameType.RequestN // TODO

    override fun receiveRequestN(requestN: Int) {
        limiter.updateRequests(requestN)
    }

    override fun receiveNext(payload: Payload?, complete: Boolean) {
        if (payload != null) {
            if (requestPayloads.trySend(payload).isFailure) payload.close()
        }
        if (complete) closeChannels(null)
    }

    override fun receiveError(cause: Throwable) {
        closeChannels(cause)
    }

    private fun closeChannels(cause: Throwable?) {
        requestPayloads.close(cause)
        requestNs.cancel() // no more requestN can be sent
    }
}
