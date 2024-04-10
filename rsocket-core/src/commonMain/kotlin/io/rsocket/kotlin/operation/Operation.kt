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
import io.rsocket.kotlin.payload.*

internal interface Operation : OperationInbound {
    // after `execute` is completed, no other interactions with the operation are possible
    suspend fun execute(outbound: OperationOutbound, requestPayload: Payload)

    // for requester only + responder RC
    // should not throw
    fun operationFailure(cause: Throwable) {}
}

internal inline fun Operation.handleExecutionFailure(requestPayload: Payload, block: () -> Unit) {
    try {
        block()
    } catch (cause: Throwable) {
        operationFailure(cause)
        requestPayload.close()
        throw cause
    }
}

internal data class ResponderOperationData(
    val streamId: Int,
    val requestType: FrameType,
    val initialRequest: Int,
    val requestPayload: Payload,
    val complete: Boolean,
)

// just marker interface
internal interface RequesterOperation : Operation

// just marker interface
internal interface ResponderOperation : Operation
