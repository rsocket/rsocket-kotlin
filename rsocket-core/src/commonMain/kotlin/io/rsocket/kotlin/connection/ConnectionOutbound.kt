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

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.io.*

// send/receive setup, resume, resume ok, lease, error
@RSocketTransportApi
internal abstract class ConnectionOutbound(
    private val frameCodec: FrameCodec,
) : CoroutineScope {
    protected abstract suspend fun sendConnectionFrame(frame: Buffer)
    protected abstract suspend fun createStream(handler: OperationFrameHandler): OperationOutbound

    private suspend fun sendConnectionFrame(frame: Frame): Unit = sendConnectionFrame(frameCodec.encodeFrame(frame))

    suspend fun sendError(cause: Throwable) {
        sendConnectionFrame(ErrorFrame(0, cause))
    }

    suspend fun sendKeepAlive(respond: Boolean, data: Buffer, lastPosition: Long) {
        sendConnectionFrame(KeepAliveFrame(respond, lastPosition, data))
    }

    suspend fun sendMetadataPush(metadata: Buffer) {
        ensureActiveOrClose(metadata::clear)
        return sendConnectionFrame(MetadataPushFrame(metadata))
    }

    suspend fun sendSetup(
        version: Version,
        honorLease: Boolean,
        keepAlive: KeepAlive,
        resumeToken: Buffer?,
        payloadMimeType: PayloadMimeType,
        payload: Payload,
    ): Unit = sendConnectionFrame(SetupFrame(version, honorLease, keepAlive, resumeToken, payloadMimeType, payload))

    suspend inline fun <T> executeRequest(payload: Payload, operation: RequesterOperation<T>): T {
        ensureActiveOrClose(payload::close)

        var stream: OperationOutbound? = null
        return try {
            stream = createStream(OperationFrameHandler(operation))
            operation.execute(stream, payload)
        } catch (cause: Throwable) {
            payload.close()
            throw cause
        } finally {
            stream?.close()
        }
    }

    private suspend inline fun ensureActiveOrClose(close: () -> Unit) {
        currentCoroutineContext().ensureActive(close)
        coroutineContext.ensureActive(close)
    }
}
