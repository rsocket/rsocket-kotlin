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

package io.rsocket.kotlin.internal.handler

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal abstract class FrameHandler : Closeable {
    private val data = BytePacketBuilder(NoPool)
    private val metadata = BytePacketBuilder(NoPool)
    private var hasMetadata: Boolean = false

    fun handleRequest(frame: RequestFrame) {
        if (frame.next || frame.type.isRequestType) handleNextFragment(frame)
        if (frame.complete) handleComplete()
    }

    private fun handleNextFragment(frame: RequestFrame) {
        data.writePacket(frame.payload.data)
        when (val meta = frame.payload.metadata) {
            null -> Unit
            else -> {
                hasMetadata = true
                metadata.writePacket(meta)
            }
        }
        if (frame.follows && !frame.complete) return

        val payload = Payload(data.build(), if (hasMetadata) metadata.build() else null)
        hasMetadata = false
        handleNext(payload)
    }

    protected abstract fun handleNext(payload: Payload)
    protected abstract fun handleComplete()
    abstract fun handleError(cause: Throwable)
    abstract fun handleCancel()
    abstract fun handleRequestN(n: Int)

    abstract fun cleanup(cause: Throwable?)

    override fun close() {
        data.close()
        metadata.close()
    }
}

internal interface ReceiveFrameHandler {
    fun onReceiveComplete()
    fun onReceiveCancelled(cause: Throwable): Boolean // if true, then request is cancelled
}

internal interface SendFrameHandler {
    fun onSendComplete()
    fun onSendFailed(cause: Throwable): Boolean // if true, then request is failed
}

internal abstract class RequesterFrameHandler : FrameHandler(), ReceiveFrameHandler {
    override fun handleCancel() {
        //should be called only for RC
    }

    override fun handleRequestN(n: Int) {
        //should be called only for RC
    }
}

internal abstract class ResponderFrameHandler : FrameHandler(), SendFrameHandler {
    protected var job: Job? = null

    protected abstract fun start(payload: Payload): Job

    final override fun handleNext(payload: Payload) {
        if (job == null) job = start(payload)
        else handleNextPayload(payload)
    }

    protected open fun handleNextPayload(payload: Payload) {
        //should be called only for RC
    }

    override fun handleComplete() {
        //should be called only for RC
    }

    override fun handleError(cause: Throwable) {
        //should be called only for RC
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
