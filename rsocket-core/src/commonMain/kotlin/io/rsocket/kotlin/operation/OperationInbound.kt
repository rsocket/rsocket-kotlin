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
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*

internal interface OperationInbound {
    fun isFrameExpected(frameType: FrameType): Boolean

    // payload is null when `next` flag was not set
    fun receiveNext(payload: Payload?, complete: Boolean) {}
    fun receiveRequestN(requestN: Int) {}
    fun receiveError(cause: Throwable) {}
    fun receiveCancel() {}

    // failure is coming not from the remote party, but somewhere from implementation
    fun receiveProcessingError(cause: Throwable)
}

internal class OperationFrameHandler(private val inbound: OperationInbound) : Closeable {
    private var hasPayload: Boolean = false
    private var hasMetadata: Boolean = false

    private val data = BytePacketBuilder(NoPool)
    private val metadata = BytePacketBuilder(NoPool)

    override fun close() {
        data.close()
        metadata.close()
    }

    fun receiveProcessingError(cause: Throwable) {
        inbound.receiveProcessingError(cause)
    }

    fun handleFrame(frame: Frame) {
        if (!inbound.isFrameExpected(frame.type)) return frame.close()

        when (frame) {
            is CancelFrame   -> inbound.receiveCancel()
            is ErrorFrame    -> inbound.receiveError(frame.throwable)
            is RequestNFrame -> inbound.receiveRequestN(frame.requestN)
            is RequestFrame  -> {
                inbound.receiveRequestN(frame.initialRequest)
                // TODO: recheck notes
                // TODO: if there are no fragments saved and there are no following - we can ignore going through buffer
                // TODO: really, fragment could be NULL when `complete` is true, but `next` is false
                if (frame.next || frame.type.isRequestType) appendFragment(frame.payload)
                if (frame.complete) inbound.receiveNext(assemblePayload(), complete = true)
                else if (!frame.follows) inbound.receiveNext(assemblePayload(), complete = false)
            }

            else             -> error("should not happen")
        }
    }

    private fun appendFragment(fragment: Payload) {
        hasPayload = true
        data.writePacket(fragment.data)

        val meta = fragment.metadata ?: return

        hasMetadata = true
        metadata.writePacket(meta)
    }

    private fun assemblePayload(): Payload? {
        if (!hasPayload) return null

        val payload = Payload(
            data = data.build(),
            metadata = when {
                hasMetadata -> metadata.build()
                else        -> null
            }
        )
        hasMetadata = false
        hasPayload = false
        return payload
    }

}

@Suppress("DEPRECATION")
private object NoPool : ObjectPool<ChunkBuffer> {
    override val capacity: Int
        get() = error("should not be called")

    override fun borrow(): ChunkBuffer {
        error("should not be called")
    }

    override fun dispose() {
        error("should not be called")
    }

    override fun recycle(instance: ChunkBuffer) {
        error("should not be called")
    }
}
