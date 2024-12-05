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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.operation.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlin.coroutines.*

@OptIn(RSocketTransportApi::class)
internal class Requester(
    override val coroutineContext: CoroutineContext,
    private val outbound: ConnectionOutbound,
) : RSocket {

    override suspend fun metadataPush(
        metadata: Buffer,
    ): Unit = outbound.sendMetadataPush(metadata)

    override suspend fun fireAndForget(
        payload: Payload,
    ): Unit = outbound.executeRequest(payload, RequesterFireAndForgetOperation())

    override suspend fun requestResponse(
        payload: Payload,
    ): Payload = outbound.executeRequest(payload, RequesterRequestResponseOperation())

    @OptIn(ExperimentalStreamsApi::class)
    override fun requestStream(
        payload: Payload,
    ): Flow<Payload> = payloadFlow { strategy, initialRequest ->
        outbound.executeRequest(payload, RequesterRequestStreamOperation(this, strategy, initialRequest))
    }

    @OptIn(ExperimentalStreamsApi::class)
    override fun requestChannel(
        initPayload: Payload,
        payloads: Flow<Payload>,
    ): Flow<Payload> = payloadFlow { strategy, initialRequest ->
        // TODO: we should not close the stream on completion
        outbound.executeRequest(initPayload, RequesterRequestChannelOperation(payloads, this, strategy, initialRequest))
    }
}
