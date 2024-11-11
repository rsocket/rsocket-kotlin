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
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.io.*
import kotlin.math.*

private const val lengthSize = 3
private const val headerSize = 6
private const val fragmentOffset = lengthSize + headerSize
private const val fragmentOffsetWithMetadata = fragmentOffset + lengthSize

internal abstract class OperationOutbound(
    protected val streamId: Int,
    private val frameCodec: FrameCodec,
) {
    // TODO: decide on it
    // private var firstRequestFrameSent: Boolean = false

    abstract val isClosed: Boolean

    protected abstract suspend fun sendFrame(frame: Buffer)
    private suspend fun sendFrame(frame: Frame): Unit = sendFrame(frameCodec.encodeFrame(frame))

    suspend fun sendError(cause: Throwable) {
        return sendFrame(ErrorFrame(streamId, cause))
    }

    suspend fun sendCancel() {
        return sendFrame(CancelFrame(streamId))
    }

    suspend fun sendRequestN(requestN: Int) {
        return sendFrame(RequestNFrame(streamId, requestN))
    }

    suspend fun sendComplete() {
        return sendFrame(
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

    suspend fun sendNext(payload: Payload, complete: Boolean) {
        return sendRequestPayload(FrameType.Payload, payload, complete, initialRequest = 0)
    }

    suspend fun sendRequest(type: FrameType, payload: Payload, complete: Boolean, initialRequest: Int) {
        return sendRequestPayload(type, payload, complete, initialRequest)
    }

    // TODO rework/simplify later
    // TODO release on fail ?
    private suspend fun sendRequestPayload(type: FrameType, payload: Payload, complete: Boolean, initialRequest: Int) {
        if (!payload.isFragmentable(type.hasInitialRequest)) {
            return sendFrame(RequestFrame(type, streamId, false, complete, true, initialRequest, payload))
        }

        val data = payload.data
        val metadata = payload.metadata

        val fragmentSize = frameCodec.maxFragmentSize - fragmentOffset - (if (type.hasInitialRequest) Int.SIZE_BYTES else 0)

        var first = true
        var remaining = fragmentSize
        if (metadata != null) remaining -= lengthSize

        do {
            val metadataFragment = if (metadata != null && !metadata.exhausted()) {
                if (!first) remaining -= lengthSize
                val length = min(metadata.size.toInt(), remaining)
                remaining -= length
                metadata.readBuffer(length)
            } else null

            val dataFragment = if (remaining > 0 && !data.exhausted()) {
                val length = min(data.size.toInt(), remaining)
                remaining -= length
                data.readBuffer(length)
            } else {
                EmptyBuffer
            }

            val fType = if (first && type.isRequestType) type else FrameType.Payload
            val fragment = Payload(dataFragment, metadataFragment)
            val follows = metadata != null && !metadata.exhausted() || !data.exhausted()
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
            first = false
            remaining = fragmentSize
        } while (follows)
    }

    private fun Payload.isFragmentable(hasInitialRequest: Boolean) = when (frameCodec.maxFragmentSize) {
        0    -> false
        else -> when (val meta = metadata) {
            null -> data.size > frameCodec.maxFragmentSize - fragmentOffset - (if (hasInitialRequest) Int.SIZE_BYTES else 0)
            else -> data.size + meta.size > frameCodec.maxFragmentSize - fragmentOffsetWithMetadata - (if (hasInitialRequest) Int.SIZE_BYTES else 0)
        }
    }

}
