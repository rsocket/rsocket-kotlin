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
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

public sealed interface ConnectedRSocket : RSocket {
    public val session: RSocketSession
}

public interface RSocket {

    public suspend fun metadataPush(metadata: ByteReadPacket): Unit =
        notImplementedMetadataPush(metadata)

    public suspend fun fireAndForget(payload: Payload): Unit =
        notImplementedFireAndForget(payload)

    public suspend fun requestResponse(payload: Payload): Payload =
        notImplementedRequestResponse(payload)

    public fun requestStream(payload: Payload): Flow<Payload> =
        notImplementedRequestStream(payload)

    public fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> =
        notImplementedRequestChannel(initPayload, payloads)

}

public sealed interface RSocketBuilder {
    public fun onMetadataPush(block: suspend RSocket.(metadata: ByteReadPacket) -> Unit)
    public fun onFireAndForget(block: suspend RSocket.(payload: Payload) -> Unit)
    public fun onRequestResponse(block: suspend RSocket.(payload: Payload) -> Payload)
    public fun onRequestStream(block: suspend RSocket.(payload: Payload) -> Flow<Payload>)
    public fun onRequestChannel(block: suspend RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload>)
}

public inline fun RSocket(block: RSocketBuilder.() -> Unit): RSocket = RSocketImpl().apply(block)

public inline fun RSocket.requestStream(crossinline payloadProvider: suspend () -> Payload): Flow<Payload> = flow {
    val payload = payloadProvider()
    val response = requestStream(payload)
    emitAll(response)
}

public inline fun RSocket.requestChannel(
    payloads: Flow<Payload>,
    crossinline initPayloadProvider: suspend () -> Payload,
): Flow<Payload> = flow {
    val initPayload = initPayloadProvider()
    val response = requestChannel(initPayload, payloads)
    emitAll(response)
}

/**
 * Tries to emit [value], if emit failed, f.e. due cancellation, calls [Closeable.close] on [value].
 * Better to use it instead of [FlowCollector.emit] with [Payload] or [ByteReadPacket] to avoid leaks of dropped elements.
 */
public suspend fun <C : Closeable> FlowCollector<C>.emitOrClose(value: C) {
    try {
        return emit(value)
    } catch (e: Throwable) {
        value.close()
        throw e
    }
}

internal abstract class ConnectedRSocketImpl(
    final override val coroutineContext: CoroutineContext,
) : ConnectedRSocket, RSocketSession {
    final override val session: RSocketSession get() = this
}

internal object EmptyRSocket : RSocket

@PublishedApi
internal class RSocketImpl : RSocketBuilder, RSocket {
    private var metadataPush: suspend RSocket.(metadata: ByteReadPacket) -> Unit =
        notImplementedMetadataPush
    private var fireAndForget: suspend RSocket.(payload: Payload) -> Unit =
        notImplementedFireAndForget
    private var requestResponse: suspend RSocket.(payload: Payload) -> Payload =
        notImplementedRequestResponse
    private var requestStream: RSocket.(payload: Payload) -> Flow<Payload> =
        notImplementedRequestStream
    private var requestChannel: RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload> =
        notImplementedRequestChannel

    override suspend fun metadataPush(metadata: ByteReadPacket) =
        metadataPush.invoke(this, metadata)

    override suspend fun fireAndForget(payload: Payload) =
        fireAndForget.invoke(this, payload)

    override suspend fun requestResponse(payload: Payload): Payload =
        requestResponse.invoke(this, payload)

    override fun requestStream(payload: Payload): Flow<Payload> =
        requestStream.invoke(this, payload)

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> =
        requestChannel.invoke(this, initPayload, payloads)

    override fun onMetadataPush(block: suspend RSocket.(metadata: ByteReadPacket) -> Unit) {
        check(metadataPush === notImplementedMetadataPush) { "Metadata Push handler already configured" }
        metadataPush = block
    }

    override fun onFireAndForget(block: suspend RSocket.(payload: Payload) -> Unit) {
        check(fireAndForget === notImplementedFireAndForget) { "Fire and Forget handler already configured" }
        fireAndForget = block
    }

    override fun onRequestResponse(block: suspend RSocket.(payload: Payload) -> Payload) {
        check(requestResponse === notImplementedRequestResponse) { "Request Response handler already configured" }
        requestResponse = block
    }

    override fun onRequestStream(block: suspend RSocket.(payload: Payload) -> Flow<Payload>) {
        check(requestStream === notImplementedRequestStream) { "Request Stream handler already configured" }
        requestStream = { payload -> flow { emitAll(block(payload)) } }
    }

    override fun onRequestChannel(block: suspend RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload>) {
        check(requestChannel === notImplementedRequestChannel) { "Request Channel handler already configured" }
        requestChannel = { initPayload, payloads -> flow { emitAll(block(initPayload, payloads)) } }
    }
}

private inline fun notImplemented(operation: String): Nothing =
    throw NotImplementedError("$operation is not implemented.")

private val notImplementedMetadataPush: suspend RSocket.(metadata: ByteReadPacket) -> Unit =
    { metadata ->
        metadata.close()
        notImplemented("Metadata Push")
    }

private val notImplementedFireAndForget: suspend RSocket.(payload: Payload) -> Unit =
    { payload ->
        payload.close()
        notImplemented("Fire and Forget")
    }

private val notImplementedRequestResponse: suspend RSocket.(payload: Payload) -> Payload =
    { payload ->
        payload.close()
        notImplemented("Request Response")
    }

private val notImplementedRequestStream: RSocket.(payload: Payload) -> Flow<Payload> =
    { payload ->
        payload.close()
        notImplemented("Request Stream")
    }

private val notImplementedRequestChannel: RSocket.(initPayload: Payload, payloads: Flow<Payload>) -> Flow<Payload> =
    { payload, _ ->
        payload.close()
        notImplemented("Request Channel")
    }
