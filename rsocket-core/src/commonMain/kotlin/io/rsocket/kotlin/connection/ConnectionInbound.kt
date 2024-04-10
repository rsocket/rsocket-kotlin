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
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@RSocketTransportApi
internal class ConnectionInbound(
    // requestContext
    override val coroutineContext: CoroutineContext,
    private val responder: RSocket,
    private val keepAliveHandler: KeepAliveHandler,
) : CoroutineScope {
    fun handleFrame(frame: Frame): Unit = when (frame) {
        is MetadataPushFrame -> receiveMetadataPush(frame.metadata)
        is KeepAliveFrame    -> receiveKeepAlive(frame.respond, frame.data, frame.lastPosition)
        is ErrorFrame        -> receiveError(frame.throwable)
        is LeaseFrame        -> receiveLease(frame.ttl, frame.numberOfRequests, frame.metadata)
        // ignore other frames
        else                 -> frame.close()
    }

    private fun receiveMetadataPush(metadata: ByteReadPacket) {
        launch {
            responder.metadataPush(metadata)
        }.invokeOnCompletion { metadata.close() }
    }

    @Suppress("UNUSED_PARAMETER") // will be used later
    private fun receiveKeepAlive(respond: Boolean, data: ByteReadPacket, lastPosition: Long) {
        keepAliveHandler.receive(data, respond)
    }

    @Suppress("UNUSED_PARAMETER") // will be used later
    private fun receiveLease(ttl: Int, numberOfRequests: Int, metadata: ByteReadPacket?) {
        metadata?.close()
        error("Lease is not supported")
    }

    private fun receiveError(cause: Throwable) {
        throw cause // TODO?
    }

    fun createOperation(type: FrameType, requestJob: Job): ResponderOperation = when (type) {
        FrameType.RequestFnF      -> ResponderFireAndForgetOperation(requestJob, responder)
        FrameType.RequestResponse -> ResponderRequestResponseOperation(requestJob, responder)
        FrameType.RequestStream   -> ResponderRequestStreamOperation(requestJob, responder)
        FrameType.RequestChannel  -> ResponderRequestChannelOperation(requestJob, responder)
        else                      -> error("should happen")
    }
}
