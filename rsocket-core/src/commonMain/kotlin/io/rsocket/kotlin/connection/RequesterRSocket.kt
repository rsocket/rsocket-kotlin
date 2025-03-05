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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlin.coroutines.*

internal class RequesterRSocket(
    private val requestsScope: CoroutineScope,
    private val outbound: ConnectionOutbound,
) : RSocket {
    override val coroutineContext: CoroutineContext get() = requestsScope.coroutineContext

    override suspend fun metadataPush(metadata: Buffer) {
        ensureActiveOrClose(metadata::clear)
        outbound.sendMetadataPush(metadata)
    }

    override suspend fun fireAndForget(payload: Payload) {
        ensureActiveOrClose(payload::close)

        suspendCancellableCoroutine { cont ->
            val requestJob = outbound.launchRequest(
                requestPayload = payload,
                operation = RequesterFireAndForgetOperation(cont)
            )
            cont.invokeOnCancellation { cause ->
                requestJob.cancel("Request was cancelled", cause)
            }
        }
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        ensureActiveOrClose(payload::close)

        val responseDeferred = CompletableDeferred<Payload>()

        val requestJob = outbound.launchRequest(
            requestPayload = payload,
            operation = RequesterRequestResponseOperation(responseDeferred)
        )

        try {
            responseDeferred.join()
        } catch (cause: Throwable) {
            requestJob.cancel("Request was cancelled", cause)
            throw cause
        }
        return responseDeferred.await()
    }

    @OptIn(ExperimentalStreamsApi::class)
    override fun requestStream(
        payload: Payload,
    ): Flow<Payload> = payloadFlow { strategy, initialRequest ->
        ensureActiveOrClose(payload::close)

        val responsePayloads = PayloadChannel()

        val requestJob = outbound.launchRequest(
            requestPayload = payload,
            operation = RequesterRequestStreamOperation(initialRequest, responsePayloads)
        )

        throw try {
            responsePayloads.consumeInto(this, strategy)
        } catch (cause: Throwable) {
            requestJob.cancel("Request was cancelled", cause)
            throw cause
        } ?: return@payloadFlow
    }

    @OptIn(ExperimentalStreamsApi::class)
    override fun requestChannel(
        initPayload: Payload,
        payloads: Flow<Payload>,
    ): Flow<Payload> = payloadFlow { strategy, initialRequest ->
        ensureActiveOrClose(initPayload::close)

        val responsePayloads = PayloadChannel()

        val requestJob = outbound.launchRequest(
            initPayload,
            RequesterRequestChannelOperation(initialRequest, payloads, responsePayloads)
        )

        throw try {
            responsePayloads.consumeInto(this, strategy)
        } catch (cause: Throwable) {
            requestJob.cancel("Request was cancelled", cause)
            throw cause
        } ?: return@payloadFlow
    }

    private suspend inline fun ensureActiveOrClose(onInactive: () -> Unit) {
        currentCoroutineContext().ensureActive(onInactive)
        coroutineContext.ensureActive(onInactive)
    }

    private inline fun CoroutineContext.ensureActive(onInactive: () -> Unit) {
        if (isActive) return
        onInactive() // should not throw
        ensureActive() // will throw
    }

}
