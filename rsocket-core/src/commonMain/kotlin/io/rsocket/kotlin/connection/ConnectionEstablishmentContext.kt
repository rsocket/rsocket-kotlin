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
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.io.*

// send/receive setup, resume, resume ok, lease, error
@RSocketTransportApi
internal abstract class ConnectionEstablishmentContext(
    private val frameCodec: FrameCodec,
) {
    protected abstract suspend fun receiveFrameRaw(): Buffer?
    protected abstract suspend fun sendFrame(frame: Buffer)
    private suspend fun sendFrame(frame: Frame): Unit = sendFrame(frameCodec.encodeFrame(frame))

    // only setup|lease|resume|resume_ok|error frames
    suspend fun receiveFrame(): Frame = frameCodec.decodeFrame(
        expectedStreamId = 0,
        frame = receiveFrameRaw() ?: error("Expected frame during connection establishment but nothing was received")
    )

    suspend fun sendSetup(
        version: Version,
        honorLease: Boolean,
        keepAlive: KeepAlive,
        resumeToken: Buffer?,
        payloadMimeType: PayloadMimeType,
        payload: Payload,
    ): Unit = sendFrame(SetupFrame(version, honorLease, keepAlive, resumeToken, payloadMimeType, payload))
}
