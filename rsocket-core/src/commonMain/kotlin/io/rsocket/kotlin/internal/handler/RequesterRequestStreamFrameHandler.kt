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

import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.channels.*

internal class RequesterRequestStreamFrameHandler(
    private val id: Int,
    private val streamsStorage: StreamsStorage,
    private val channel: Channel<Payload>,
) : RequesterFrameHandler() {

    override fun handleNext(payload: Payload) {
        channel.safeTrySend(payload)
    }

    override fun handleComplete() {
        channel.close()
    }

    override fun handleError(cause: Throwable) {
        streamsStorage.remove(id)
        channel.cancelWithCause(cause)
    }

    override fun cleanup(cause: Throwable?) {
        channel.cancelWithCause(cause)
    }

    override fun onReceiveComplete() {
        streamsStorage.remove(id)
    }

    override fun onReceiveCancelled(cause: Throwable): Boolean = streamsStorage.remove(id) != null
}
