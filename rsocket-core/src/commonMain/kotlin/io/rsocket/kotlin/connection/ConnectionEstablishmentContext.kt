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
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*

// send/receive setup, resume, resume ok, lease, error
internal interface ConnectionEstablishmentContext {
    // only setup|lease|resume|resume_ok|error frames
    suspend fun receiveFrame(): Frame

    suspend fun sendSetup(
        version: Version,
        honorLease: Boolean,
        keepAlive: KeepAlive,
        resumeToken: ByteReadPacket?,
        payloadMimeType: PayloadMimeType,
        payload: Payload,
    )
}

internal interface ConnectionEstablishmentHandler {
    val isClient: Boolean
    suspend fun establishConnection(context: ConnectionEstablishmentContext): ConnectionConfig
}

internal abstract class AbstractConnectionEstablishmentContext(
    private val bufferPool: BufferPool,
) : ConnectionEstablishmentContext {
    protected abstract suspend fun sendFrame(frame: ByteReadPacket)
    protected abstract suspend fun receiveFrameRaw(): ByteReadPacket
    private suspend fun sendFrame(frame: Frame): Unit = sendFrame(frame.toPacket(bufferPool))
    override suspend fun receiveFrame(): Frame = receiveFrameRaw().readFrame(bufferPool)

    override suspend fun sendSetup(
        version: Version,
        honorLease: Boolean,
        keepAlive: KeepAlive,
        resumeToken: ByteReadPacket?,
        payloadMimeType: PayloadMimeType,
        payload: Payload,
    ): Unit = sendFrame(SetupFrame(version, honorLease, keepAlive, resumeToken, payloadMimeType, payload))
}
