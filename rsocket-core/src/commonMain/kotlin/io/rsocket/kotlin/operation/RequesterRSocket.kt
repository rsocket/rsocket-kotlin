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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

internal class RequesterRSocket(
    override val coroutineContext: CoroutineContext,
    private val metadataFrames: SendChannel<ByteReadPacket>,
    private val executor: RequesterOperationExecutor,
) : RSocket {

    override suspend fun metadataPush(metadata: ByteReadPacket) {
        metadataFrames.send(metadata)
    }

    override suspend fun fireAndForget(payload: Payload) {
        val requestSentDeferred = CompletableDeferred<Unit>()
        val requestJob = executor.executeRequest(
            payload = payload,
            complete = false,
            initialRequest = 0,
            operation = RequesterFireAndForgetOperation(requestSentDeferred)
        )
        try {
            requestSentDeferred.join()
        } catch (cause: Throwable) {
            requestJob.cancel("Request cancelled", cause)
            requestSentDeferred.cancel()
            throw cause
        }
        return requestSentDeferred.await()
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        val responseDeferred = CompletableDeferred<Payload>()
        val requestJob = executor.executeRequest(
            payload = payload,
            complete = false,
            initialRequest = 0,
            operation = RequesterRequestResponseOperation(responseDeferred)
        )
        try {
            responseDeferred.join()
        } catch (cause: Throwable) {
            requestJob.cancel("Request cancelled", cause)
            responseDeferred.cancel()
            throw cause
        }
        return responseDeferred.await()
    }

    @OptIn(ExperimentalStreamsApi::class)
    override fun requestStream(payload: Payload): Flow<Payload> = requestFlow { strategy, initialRequest ->
        val responsePayloads = channelForCloseable<Payload>(Channel.UNLIMITED) // TODO: should be configurable
        val requestNs = Channel<Int>(Channel.UNLIMITED)

        val requestJob = executor.executeRequest(
            payload = payload,
            complete = false,
            initialRequest = initialRequest,
            operation = RequesterRequestStreamOperation(requestNs, responsePayloads)
        )

        val error = try {
            emitAllWithRequestN(responsePayloads, requestNs, strategy)
        } catch (cause: Throwable) {
            requestJob.cancel("Request cancelled", cause)
            throw cause
        } finally {
            responsePayloads.cancel() // no more payloads can be received
            requestNs.cancel() // no more requestN can be sent
        }
        throw error ?: return@requestFlow
    }

    @OptIn(ExperimentalStreamsApi::class)
    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = requestFlow { strategy, initialRequest ->
        val responsePayloads = channelForCloseable<Payload>(Channel.UNLIMITED) // TODO: should be configurable
        val requestNs = Channel<Int>(Channel.UNLIMITED)

        val requestJob = executor.executeRequest(
            payload = initPayload,
            complete = false,
            initialRequest = initialRequest,
            operation = RequesterRequestChannelOperation(requestNs, responsePayloads, payloads)
        )

        val error = try {
            emitAllWithRequestN(responsePayloads, requestNs, strategy)
        } catch (cause: Throwable) {
            requestJob.cancel("Request cancelled", cause)
            throw cause
        } finally {
            responsePayloads.cancel() // no more payloads can be received
            requestNs.cancel() // no more requestN can be sent
        }
        throw error ?: return@requestFlow
    }
}
