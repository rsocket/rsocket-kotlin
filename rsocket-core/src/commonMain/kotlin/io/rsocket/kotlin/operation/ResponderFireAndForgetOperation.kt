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
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal class ResponderFireAndForgetOperation(
    private val requestJob: Job,
    private val responder: RSocket,
) : ResponderOperation {

    override suspend fun execute(outbound: OperationOutbound, requestPayload: Payload) {
        responder.fireAndForget(requestPayload)
    }

    override fun shouldReceiveFrame(frameType: FrameType): Boolean = frameType === FrameType.Cancel

    override fun receiveCancelFrame() {
        requestJob.cancel("Request was cancelled by remote party")
    }
}
