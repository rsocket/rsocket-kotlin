/*
 * Copyright 2015-2022 the original author or authors.
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
import io.rsocket.kotlin.internal.handler.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(ExperimentalStreamsApi::class)
internal class RSocketResponder(
    override val coroutineContext: CoroutineContext,
    private val sender: FrameSender,
    private val requestHandler: RSocket
) : CoroutineScope {

    fun handleMetadataPush(metadata: ByteReadPacket): Job = launch {
        requestHandler.metadataPush(metadata)
    }.closeOnCompletion(metadata)

    fun handleFireAndForget(payload: Payload, handler: ResponderFireAndForgetFrameHandler): Job = launch {
        try {
            requestHandler.fireAndForget(payload)
        } finally {
            handler.onSendComplete()
        }
    }.closeOnCompletion(payload)

    fun handleRequestResponse(payload: Payload, id: Int, handler: ResponderRequestResponseFrameHandler): Job = launch {
        handler.sendOrFail(id, payload) {
            val response = requestHandler.requestResponse(payload)
            sender.sendNextCompletePayload(id, response)
        }
    }.closeOnCompletion(payload)

    fun handleRequestStream(payload: Payload, id: Int, handler: ResponderRequestStreamFrameHandler): Job = launch {
        handler.sendOrFail(id, payload) {
            requestHandler.requestStream(payload).collectLimiting(handler.limiter) { sender.sendNextPayload(id, it) }
            sender.sendCompletePayload(id)
        }
    }.closeOnCompletion(payload)

    fun handleRequestChannel(payload: Payload, id: Int, handler: ResponderRequestChannelFrameHandler): Job = launch {
        val payloads = requestFlow { strategy, initialRequest ->
            handler.receiveOrCancel(id) {
                sender.sendRequestN(id, initialRequest)
                emitAllWithRequestN(handler.channel, strategy) { sender.sendRequestN(id, it) }
            }
        }
        handler.sendOrFail(id, payload) {
            requestHandler.requestChannel(payload, payloads).collectLimiting(handler.limiter) { sender.sendNextPayload(id, it) }
            sender.sendCompletePayload(id)
        }
    }.closeOnCompletion(payload)

    private suspend inline fun SendFrameHandler.sendOrFail(id: Int, payload: Payload, block: () -> Unit) {
        try {
            block()
            onSendComplete()
        } catch (cause: Throwable) {
            val isFailed = onSendFailed(cause)
            if (currentCoroutineContext().isActive && isFailed) sender.sendError(id, cause)
            throw cause
        } finally {
            payload.close()
        }
    }

    private suspend inline fun ReceiveFrameHandler.receiveOrCancel(id: Int, block: () -> Unit) {
        try {
            block()
            onReceiveComplete()
        } catch (cause: Throwable) {
            val isCancelled = onReceiveCancelled(cause)
            if (isActive && isCancelled) sender.sendCancel(id)
            throw cause
        }
    }

    private fun Job.closeOnCompletion(closeable: Closeable): Job {
        invokeOnCompletion {
            closeable.close()
        }
        return this
    }

}
