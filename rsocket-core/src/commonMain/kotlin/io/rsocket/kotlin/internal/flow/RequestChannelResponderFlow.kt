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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

//TODO prevent consuming more then one time - add atomic ?
internal class RequestChannelResponderFlow(
    private val streamId: Int,
    private val receiver: ReceiveChannel<RequestFrame>,
    state: RSocketState,
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.BUFFERED,
) : StreamFlow(state, context, capacity) {

    override fun create(context: CoroutineContext, capacity: Int): RequestChannelResponderFlow =
        RequestChannelResponderFlow(streamId, receiver, state, context, capacity)

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun collectTo(scope: ProducerScope<Payload>): Unit = with(state) {
        send(RequestNFrame(streamId, requestSize - 1)) //-1 because first payload received
        collectStream(streamId, receiver, scope)
    }
}
