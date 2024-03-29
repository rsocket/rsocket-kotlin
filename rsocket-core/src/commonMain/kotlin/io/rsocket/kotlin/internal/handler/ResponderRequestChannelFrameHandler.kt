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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class ResponderRequestChannelFrameHandler(
    private val id: Int,
    private val streamsStorage: StreamsStorage,
    private val responder: RSocketResponder,
    initialRequest: Int,
) : ResponderFrameHandler(), ReceiveFrameHandler {
    val limiter = Limiter(initialRequest)
    val channel = channelForCloseable<Payload>(Channel.UNLIMITED)

    @OptIn(ExperimentalStreamsApi::class)
    override fun start(payload: Payload): Job = responder.handleRequestChannel(payload, id, this)

    override fun handleNextPayload(payload: Payload) {
        channel.safeTrySend(payload)
    }

    override fun handleComplete() {
        channel.close()
    }

    override fun handleError(cause: Throwable) {
        streamsStorage.remove(id)
        channel.cancelWithCause(cause)
    }

    override fun handleCancel() {
        streamsStorage.remove(id)
        val cancelError = CancellationException("Request cancelled")
        channel.cancelWithCause(cancelError)
        job?.cancel(cancelError)
    }

    override fun handleRequestN(n: Int) {
        limiter.updateRequests(n)
    }

    override fun cleanup(cause: Throwable?) {
        channel.cancelWithCause(cause)
    }

    override fun onSendComplete() {
        @OptIn(DelicateCoroutinesApi::class)
        if (channel.isClosedForSend) streamsStorage.remove(id)
    }

    override fun onSendFailed(cause: Throwable): Boolean {
        val isFailed = streamsStorage.remove(id) != null
        if (isFailed) channel.cancelWithCause(cause)
        return isFailed
    }

    override fun onReceiveComplete() {
        val job = this.job!! //always not null here
        if (!job.isActive) streamsStorage.remove(id)
    }

    override fun onReceiveCancelled(cause: Throwable): Boolean {
        val job = this.job!! //always not null here
        if (!streamsStorage.contains(id) && job.isActive) job.cancel("Request handling failed [Error frame]", cause)
        return !job.isCancelled
    }
}
