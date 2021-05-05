/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

public class RSocketRequestHandlerBuilder internal constructor() {
    private var metadataPush: (suspend RSocket.(metadata: ByteReadPacket) -> Unit)? = null
    private var fireAndForget: (suspend RSocket.(payload: Payload) -> Unit)? = null
    private var requestResponse: (suspend RSocket.(payload: Payload) -> Payload)? = null
    private var requestStream: (suspend RSocket.(payload: Payload) -> Flow<Payload>)? = null
    private var requestChannel: (suspend RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload>)? = null

    public fun metadataPush(block: (suspend RSocket.(metadata: ByteReadPacket) -> Unit)) {
        check(metadataPush == null) { "Metadata Push handler already configured" }
        metadataPush = block
    }

    public fun fireAndForget(block: (suspend RSocket.(payload: Payload) -> Unit)) {
        check(fireAndForget == null) { "Fire and Forget handler already configured" }
        fireAndForget = block
    }

    public fun requestResponse(block: (suspend RSocket.(payload: Payload) -> Payload)) {
        check(requestResponse == null) { "Request Response handler already configured" }
        requestResponse = block
    }

    public fun requestStream(block: suspend (RSocket.(payload: Payload) -> Flow<Payload>)) {
        check(requestStream == null) { "Request Stream handler already configured" }
        requestStream = block
    }

    public fun requestChannel(block: suspend (RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload>)) {
        check(requestChannel == null) { "Request Channel handler already configured" }
        requestChannel = block
    }

    internal fun build(job: Job): RSocket =
        RSocketRequestHandler(job, metadataPush, fireAndForget, requestResponse, requestStream, requestChannel)
}

@Suppress("FunctionName")
public fun RSocketRequestHandler(parentJob: Job? = null, configure: RSocketRequestHandlerBuilder.() -> Unit): RSocket {
    val builder = RSocketRequestHandlerBuilder()
    builder.configure()
    return builder.build(Job(parentJob))
}

private class RSocketRequestHandler(
    override val job: Job,
    private val metadataPush: (suspend RSocket.(metadata: ByteReadPacket) -> Unit)? = null,
    private val fireAndForget: (suspend RSocket.(payload: Payload) -> Unit)? = null,
    private val requestResponse: (suspend RSocket.(payload: Payload) -> Payload)? = null,
    private val requestStream: (suspend RSocket.(payload: Payload) -> Flow<Payload>)? = null,
    private val requestChannel: (suspend RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload>)? = null,
) : RSocket {
    override suspend fun metadataPush(metadata: ByteReadPacket): Unit =
        metadataPush?.invoke(this, metadata) ?: super.metadataPush(metadata)

    override suspend fun fireAndForget(payload: Payload): Unit =
        fireAndForget?.invoke(this, payload) ?: super.fireAndForget(payload)

    override suspend fun requestResponse(payload: Payload): Payload =
        requestResponse?.invoke(this, payload) ?: super.requestResponse(payload)

    override fun requestStream(payload: Payload): Flow<Payload> {
        return if (this.requestStream != null) {
            flow { emitAll(requestStream.invoke(this@RSocketRequestHandler, payload)) }
        } else {
            super.requestStream(payload)
        }
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> {
        return if (this.requestChannel != null) {
            flow { emitAll(requestChannel.invoke(this@RSocketRequestHandler, initPayload, payloads)) }
        } else {
            super.requestChannel(initPayload, payloads)
        }
    }

}
