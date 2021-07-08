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

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal abstract class FrameHandler {

    fun handleRequest(frame: RequestFrame) {
        if (frame.next || frame.type.isRequestType) handleNextFragment(frame)
        if (frame.complete) handleComplete()
    }

    private fun handleNextFragment(frame: RequestFrame) {
        //TODO fragmentation will be here
        handleNext(frame.payload)
    }

    protected abstract fun handleNext(payload: Payload)
    protected abstract fun handleComplete()
    abstract fun handleError(cause: Throwable)
    abstract fun handleCancel()
    abstract fun handleRequestN(n: Int)

    abstract fun cleanup(cause: Throwable?)
}

internal interface ReceiveFrameHandler {
    fun onReceiveComplete()
    fun onReceiveCancelled(cause: Throwable): Boolean // if true, then request is cancelled
}

internal interface SendFrameHandler {
    fun onSendComplete()
    fun onSendFailed(cause: Throwable): Boolean // if true, then request is failed
}

internal abstract class BaseRequesterFrameHandler : FrameHandler(), ReceiveFrameHandler {
    override fun handleCancel() {
        //should be called only for RC
    }

    override fun handleRequestN(n: Int) {
        //should be called only for RC
    }
}

internal abstract class BaseResponderFrameHandler : FrameHandler(), SendFrameHandler {
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

internal expect abstract class ResponderFrameHandler() : BaseResponderFrameHandler {
    override var job: Job?
    //TODO fragmentation will be here
}

internal expect abstract class RequesterFrameHandler() : BaseRequesterFrameHandler {
    //TODO fragmentation will be here
}
