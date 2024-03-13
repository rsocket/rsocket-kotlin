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
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlin.math.*

internal interface OperationOutbound : Closeable {
    // should be called only once by Requester
    suspend fun sendRequest(type: RSocketOperationType, payload: Payload, complete: Boolean, initialRequest: Int)

    suspend fun sendNext(payload: Payload, complete: Boolean)
    suspend fun sendComplete()
    suspend fun sendError(cause: Throwable)
    suspend fun sendCancel()
    suspend fun sendRequestN(requestN: Int)
}

private const val lengthSize = 3
private const val headerSize = 6
private const val fragmentOffset = lengthSize + headerSize
private const val fragmentOffsetWithMetadata = fragmentOffset + lengthSize

internal abstract class AbstractOperationOutbound(
    protected val streamId: Int,
    private val maxFragmentSize: Int,
    protected val bufferPool: BufferPool,
) : OperationOutbound {
    // TODO: decide on it
    private var firstRequestFrameSent: Boolean = false

    protected abstract suspend fun sendFrame(frame: ByteReadPacket)
    private suspend fun sendFrame(frame: Frame): Unit = sendFrame(frame.toPacket(bufferPool))

    override suspend fun sendError(cause: Throwable) {
        sendFrame(ErrorFrame(streamId, cause))
    }

    override suspend fun sendCancel() {
        sendFrame(CancelFrame(streamId))
    }

    override suspend fun sendRequestN(requestN: Int) {
        sendFrame(RequestNFrame(streamId, requestN))
    }

    override suspend fun sendComplete() {
        sendFrame(
            RequestFrame(
                type = FrameType.Payload,
                streamId = streamId,
                follows = false,
                complete = true,
                next = false,
                initialRequest = 0,
                payload = Payload.Empty
            )
        )
    }

    override suspend fun sendNext(payload: Payload, complete: Boolean) {
        sendRequestPayload(FrameType.Payload, payload, complete, initialRequest = 0)
    }

    override suspend fun sendRequest(type: RSocketOperationType, payload: Payload, complete: Boolean, initialRequest: Int) {
        val frameType = when (type) {
            RSocketOperationType.FireAndForget   -> FrameType.RequestFnF
            RSocketOperationType.RequestResponse -> FrameType.RequestResponse
            RSocketOperationType.RequestStream   -> FrameType.RequestStream
            RSocketOperationType.RequestChannel  -> FrameType.RequestChannel
        }
        sendRequestPayload(frameType, payload, complete, initialRequest)
    }

    private suspend fun sendRequestPayload(type: FrameType, payload: Payload, complete: Boolean, initialRequest: Int) {
        // TODO rework/simplify later
        // TODO release on fail ?
        if (!payload.isFragmentable(type.hasInitialRequest)) {
            sendFrame(RequestFrame(type, streamId, false, complete, true, initialRequest, payload))
            if (type.isRequestType) firstRequestFrameSent = true
            return
        }

        val data = payload.data
        val metadata = payload.metadata

        val fragmentSize = maxFragmentSize - fragmentOffset - (if (type.hasInitialRequest) Int.SIZE_BYTES else 0)

        var first = true
        var remaining = fragmentSize
        if (metadata != null) remaining -= lengthSize

        do {
            val metadataFragment = if (metadata != null && metadata.isNotEmpty) {
                if (!first) remaining -= lengthSize
                val length = min(metadata.remaining.toInt(), remaining)
                remaining -= length
                metadata.readPacket(bufferPool, length)
            } else null

            val dataFragment = if (remaining > 0 && data.isNotEmpty) {
                val length = min(data.remaining.toInt(), remaining)
                remaining -= length
                data.readPacket(bufferPool, length)
            } else {
                ByteReadPacket.Empty
            }

            val fType = if (first && type.isRequestType) type else FrameType.Payload
            val fragment = Payload(dataFragment, metadataFragment)
            val follows = metadata != null && metadata.isNotEmpty || data.isNotEmpty
            sendFrame(
                RequestFrame(
                    type = fType,
                    streamId = streamId,
                    follows = follows,
                    complete = (!follows && complete),
                    next = !fType.isRequestType,
                    initialRequest = initialRequest,
                    payload = fragment
                )
            )
            if (first && type.isRequestType) firstRequestFrameSent = true
            first = false
            remaining = fragmentSize
        } while (follows)
    }

    private fun Payload.isFragmentable(hasInitialRequest: Boolean) = when (maxFragmentSize) {
        0 -> false
        else -> when (val meta = metadata) {
            null -> data.remaining > maxFragmentSize - fragmentOffset - (if (hasInitialRequest) Int.SIZE_BYTES else 0)
            else -> data.remaining + meta.remaining > maxFragmentSize - fragmentOffsetWithMetadata - (if (hasInitialRequest) Int.SIZE_BYTES else 0)
        }
    }

}
