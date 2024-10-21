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

package io.rsocket.kotlin.connection

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

// TODO: rename to just `Connection` after root `Connection` will be dropped
@RSocketTransportApi
internal abstract class Connection2(
    protected val frameCodec: FrameCodec,
    // requestContext
    final override val coroutineContext: CoroutineContext,
) : RSocket, Closeable {

    // connection establishment part

    abstract suspend fun establishConnection(handler: ConnectionEstablishmentHandler): ConnectionConfig

    // setup completed, start handling requests
    abstract suspend fun handleConnection(inbound: ConnectionInbound)

    // connection part

    protected abstract suspend fun sendConnectionFrame(frame: ByteReadPacket)
    private suspend fun sendConnectionFrame(frame: Frame): Unit = sendConnectionFrame(frameCodec.encodeFrame(frame))

    suspend fun sendError(cause: Throwable) {
        sendConnectionFrame(ErrorFrame(0, cause))
    }

    private suspend fun sendMetadataPush(metadata: ByteReadPacket) {
        sendConnectionFrame(MetadataPushFrame(metadata))
    }

    suspend fun sendKeepAlive(respond: Boolean, data: ByteReadPacket, lastPosition: Long) {
        sendConnectionFrame(KeepAliveFrame(respond, lastPosition, data))
    }

    // operations part

    protected abstract fun launchRequest(requestPayload: Payload, operation: RequesterOperation): Job
    private suspend fun ensureActiveOrClose(closeable: Closeable) {
        currentCoroutineContext().ensureActive { closeable.close() }
        coroutineContext.ensureActive { closeable.close() }
    }

    final override suspend fun metadataPush(metadata: ByteReadPacket) {
        ensureActiveOrClose(metadata)
        sendMetadataPush(metadata)
    }

    final override suspend fun fireAndForget(payload: Payload) {
        ensureActiveOrClose(payload)

        suspendCancellableCoroutine { cont ->
            val requestJob = launchRequest(
                requestPayload = payload,
                operation = RequesterFireAndForgetOperation(cont)
            )
            cont.invokeOnCancellation { cause ->
                requestJob.cancel("Request was cancelled", cause)
            }
        }
    }

    final override suspend fun requestResponse(payload: Payload): Payload {
        ensureActiveOrClose(payload)

        val responseDeferred = CompletableDeferred<Payload>()

        val requestJob = launchRequest(
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
    final override fun requestStream(
        payload: Payload,
    ): Flow<Payload> = payloadFlow { strategy, initialRequest ->
        ensureActiveOrClose(payload)

        val responsePayloads = PayloadChannel()

        val requestJob = launchRequest(
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
    final override fun requestChannel(
        initPayload: Payload,
        payloads: Flow<Payload>,
    ): Flow<Payload> = payloadFlow { strategy, initialRequest ->
        ensureActiveOrClose(initPayload)

        val responsePayloads = PayloadChannel()

        val requestJob = launchRequest(
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
}
