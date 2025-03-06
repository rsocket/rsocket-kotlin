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

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class RequesterFireAndForgetOperation(
    private val requestSentCont: CancellableContinuation<Unit>,
) : RequesterOperation {

    override suspend fun execute(outbound: OperationOutbound, requestPayload: Payload) {
        try {
            outbound.sendRequest(
                type = FrameType.RequestFnF,
                payload = requestPayload,
                complete = false,
                initialRequest = 0
            )
            requestSentCont.resume(Unit)
        } catch (cause: Throwable) {
            if (requestSentCont.isActive) requestSentCont.resumeWithException(cause)
            nonCancellable { outbound.sendCancel() }
            throw cause
        }
    }

    override fun shouldReceiveFrame(frameType: FrameType): Boolean = false

    override fun operationFailure(cause: Throwable) {
        if (requestSentCont.isActive) requestSentCont.resumeWithException(cause)
    }
}
