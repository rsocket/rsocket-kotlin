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

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class RequesterRequestChannelFrameHandler(
    private val id: Int,
    private val streamsStorage: StreamsStorage,
    private val limiter: Limiter,
    private val sender: Job,
    private val channel: Channel<Payload>,
    pool: ObjectPool<ChunkBuffer>
) : RequesterFrameHandler(pool), SendFrameHandler {

    override fun handleNext(payload: Payload) {
        channel.safeTrySend(payload)
    }

    override fun handleComplete() {
        channel.close()
    }

    override fun handleError(cause: Throwable) {
        streamsStorage.remove(id)
        channel.fullClose(cause)
        sender.cancel("Request failed", cause)
    }

    override fun handleCancel() {
        sender.cancel("Request cancelled")
    }

    override fun handleRequestN(n: Int) {
        limiter.updateRequests(n)
    }

    override fun cleanup(cause: Throwable?) {
        channel.fullClose(cause)
        sender.cancel("Connection closed", cause)
    }

    override fun onReceiveComplete() {
        if (!sender.isActive) streamsStorage.remove(id)
    }

    override fun onReceiveCancelled(cause: Throwable): Boolean {
        val isCancelled = streamsStorage.remove(id) != null
        if (isCancelled) sender.cancel("Request cancelled", cause)
        return isCancelled
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onSendComplete() {
        if (channel.isClosedForSend) streamsStorage.remove(id)
    }

    override fun onSendFailed(cause: Throwable): Boolean {
        if (sender.isCancelled) return false

        val isFailed = streamsStorage.remove(id) != null
        if (isFailed) channel.fullClose(cause)
        return isFailed
    }
}
