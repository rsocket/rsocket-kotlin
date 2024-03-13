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
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.io.*

internal interface ConnectionOutbound {
    suspend fun sendMetadataPush(metadata: ByteReadPacket)
    suspend fun sendKeepAlive(respond: Boolean, data: ByteReadPacket, lastPosition: Long)
    suspend fun sendError(cause: Throwable)
}

internal abstract class AbstractConnectionOutbound(
    private val bufferPool: BufferPool,
) : ConnectionOutbound {
    protected abstract suspend fun sendFrame(frame: ByteReadPacket)
    private suspend fun sendFrame(frame: Frame): Unit = sendFrame(frame.toPacket(bufferPool))

    override suspend fun sendError(cause: Throwable) {
        sendFrame(ErrorFrame(0, cause))
    }

    override suspend fun sendMetadataPush(metadata: ByteReadPacket) {
        sendFrame(MetadataPushFrame(metadata))
    }

    override suspend fun sendKeepAlive(respond: Boolean, data: ByteReadPacket, lastPosition: Long) {
        sendFrame(KeepAliveFrame(respond, lastPosition, data))
    }
}
