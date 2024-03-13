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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal abstract class RequesterOperation : OperationInbound {
    abstract val type: RSocketOperationType

    // after `execute` is completed, no other interactions with the operation are possible
    abstract suspend fun execute(outbound: OperationOutbound)

    open val needCancelling: Boolean get() = true
}

internal interface RequesterOperationFactory {
    suspend fun createRequest(type: RSocketOperationType, handler: OperationFrameHandler): OperationOutbound
}

internal class RequesterOperationExecutor(
    private val requestsScope: CoroutineScope,
    private val operationFactory: RequesterOperationFactory,
) {
    suspend fun executeRequest(
        payload: Payload,
        complete: Boolean,
        initialRequest: Int,
        operation: RequesterOperation,
    ): Job {
        if (!requestsScope.isActive || !currentCoroutineContext().isActive) {
            payload.close()
            currentCoroutineContext().ensureActive()
            requestsScope.ensureActive()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        return requestsScope.launch(start = CoroutineStart.ATOMIC) {
            try {
                ensureActive()
            } catch (cause: Throwable) {
                payload.close()
                operation.receiveProcessingError(cause)
                throw cause
            }
            val outbound = try {
                operationFactory.createRequest(operation.type, OperationFrameHandler(operation))
            } catch (cause: Throwable) {
                payload.close()
                operation.receiveProcessingError(cause)
                throw cause
            }

            try {
                // payload closing should be handled inside
                outbound.sendRequest(operation.type, payload, complete, initialRequest)
                operation.execute(outbound)
            } catch (cause: Throwable) {
                operation.receiveProcessingError(cause)

                // TODO: we don't need to send cancel if we have sent no frames
                if (requestsScope.isActive && operation.needCancelling) outbound.sendCancel()
                throw cause // TODO: this exception will be lost and so most likely should
            } finally {
                outbound.close()
            }
        }
    }
}
