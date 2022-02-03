/*
 * Copyright 2015-2020 the original author or authors.
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

internal abstract class FrameHandler(pool: ObjectPool<ChunkBuffer>) : Closeable {
    private val data = BytePacketBuilder(pool)
    private val metadata = BytePacketBuilder(pool)
    protected abstract var hasMetadata: Boolean

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

internal abstract class BaseRequesterFrameHandler(pool: ObjectPool<ChunkBuffer>) : FrameHandler(pool), ReceiveFrameHandler {
    override fun handleCancel() {
        //should be called only for RC
    }

    override fun handleRequestN(n: Int) {
        //should be called only for RC
    }
}

internal abstract class BaseResponderFrameHandler(pool: ObjectPool<ChunkBuffer>) : FrameHandler(pool), SendFrameHandler {
    protected abstract var job: Job?

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

internal expect abstract class ResponderFrameHandler(pool: ObjectPool<ChunkBuffer>) : BaseResponderFrameHandler {
    override var job: Job?
    override var hasMetadata: Boolean
}

internal expect abstract class RequesterFrameHandler(pool: ObjectPool<ChunkBuffer>) : BaseRequesterFrameHandler {
    override var hasMetadata: Boolean
}
