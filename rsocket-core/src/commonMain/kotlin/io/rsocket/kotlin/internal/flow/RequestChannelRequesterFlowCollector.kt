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

package io.rsocket.kotlin.internal.flow

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class RequestChannelRequesterFlowCollector(
    private val state: RSocketState,
    private val streamId: Int,
    private val receiver: CompletableDeferred<ReceiveChannel<RequestFrame>?>,
    private val requestSize: Int,
) : LimitingFlowCollector(1) {
    private val firstRequest = atomic(true) //needed for K/N
    override suspend fun emitValue(value: Payload): Unit = with(state) {
        if (firstRequest.value) {
            firstRequest.value = false
            receiver.complete(createReceiverFor(streamId))
            send(RequestChannelFrame(streamId, requestSize, value))
        } else {
            send(NextPayloadFrame(streamId, value))
        }
    }
}
