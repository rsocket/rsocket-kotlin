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
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.operation.*
import kotlinx.coroutines.*
import kotlinx.io.*

internal class ConnectionInbound(
    private val requestsScope: CoroutineScope,
    private val responder: RSocket,
    private val keepAliveHandler: KeepAliveHandler,
) {
    fun handleFrame(frame: Frame): Unit = when (frame) {
        is MetadataPushFrame -> receiveMetadataPush(frame.metadata)
        is KeepAliveFrame    -> receiveKeepAlive(frame.respond, frame.data, frame.lastPosition)
        is ErrorFrame        -> receiveError(frame.throwable)
        is LeaseFrame        -> receiveLease(frame.ttl, frame.numberOfRequests, frame.metadata)
        // ignore other frames
        else                 -> frame.close()
    }

    private fun receiveMetadataPush(metadata: Buffer) {
        requestsScope.launch {
            responder.metadataPush(metadata)
        }.invokeOnCompletion { metadata.clear() }
    }

    @Suppress("UNUSED_PARAMETER") // will be used later
    private fun receiveKeepAlive(respond: Boolean, data: Buffer, lastPosition: Long) {
        keepAliveHandler.receive(data, respond)
    }

    @Suppress("UNUSED_PARAMETER") // will be used later
    private fun receiveLease(ttl: Int, numberOfRequests: Int, metadata: Buffer?) {
        metadata?.close()
        error("Lease is not supported")
    }

    private fun receiveError(cause: Throwable) {
        throw cause
    }

    fun createOperation(type: FrameType, requestJob: Job): ResponderOperation = when (type) {
        FrameType.RequestFnF      -> ResponderFireAndForgetOperation(requestJob, responder)
        FrameType.RequestResponse -> ResponderRequestResponseOperation(requestJob, responder)
        FrameType.RequestStream   -> ResponderRequestStreamOperation(requestJob, responder)
        FrameType.RequestChannel  -> ResponderRequestChannelOperation(requestJob, responder)
        else                      -> error("should happen")
    }
}
