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
import kotlinx.coroutines.*

internal interface ConnectionInbound {
    fun receiveMetadataPush(metadata: ByteReadPacket)
    fun receiveKeepAlive(respond: Boolean, data: ByteReadPacket, lastPosition: Long)
    fun receiveLease(ttl: Int, numberOfRequests: Int, metadata: ByteReadPacket?)
    fun receiveError(cause: Throwable)
}

internal class ConnectionFrameHandler(private val inbound: ConnectionInbound) {
    fun handleFrame(frame: Frame): Unit = when (frame) {
        is MetadataPushFrame -> inbound.receiveMetadataPush(frame.metadata)
        is KeepAliveFrame    -> inbound.receiveKeepAlive(frame.respond, frame.data, frame.lastPosition)
        is ErrorFrame        -> inbound.receiveError(frame.throwable)
        is LeaseFrame        -> inbound.receiveLease(frame.ttl, frame.numberOfRequests, frame.metadata)
        // ignore other frames
        else                 -> frame.close()
    }
}

internal class ConnectionInboundImpl(
    private val sessionScope: CoroutineScope,
    private val requestsScope: CoroutineScope,
    private val responder: RSocket,
    private val keepAliveHandler: KeepAliveHandler,
) : ConnectionInbound {
    override fun receiveMetadataPush(metadata: ByteReadPacket) {
        requestsScope.launch {
            metadata.use { responder.metadataPush(it) }
        }
    }

    override fun receiveKeepAlive(respond: Boolean, data: ByteReadPacket, lastPosition: Long) {
        keepAliveHandler.receive(data, respond)
    }

    override fun receiveLease(ttl: Int, numberOfRequests: Int, metadata: ByteReadPacket?) {
        metadata?.close()
        sessionScope.cancel("Lease is not supported")
    }

    override fun receiveError(cause: Throwable) {
        sessionScope.cancel("Session received an error", cause)
    }
}
