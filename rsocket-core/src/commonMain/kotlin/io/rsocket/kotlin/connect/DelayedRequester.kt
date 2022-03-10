package io.rsocket.kotlin.connect

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.flow.*

internal sealed class DelayedRequester : RSocket {
    abstract suspend fun get(): RSocket

    private suspend inline fun getOrClose(closeable: Closeable): RSocket = closeable.closeOnError { get() }

    final override suspend fun metadataPush(metadata: ByteReadPacket) {
        return getOrClose(metadata).metadataPush(metadata)
    }

    final override suspend fun fireAndForget(payload: Payload) {
        return getOrClose(payload).fireAndForget(payload)
    }

    final override suspend fun requestResponse(payload: Payload): Payload {
        return getOrClose(payload).requestResponse(payload)
    }

    final override fun requestStream(payload: Payload): Flow<Payload> = flow {
        emitAll(getOrClose(payload).requestStream(payload))
    }

    final override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = flow {
        emitAll(getOrClose(initPayload).requestChannel(initPayload, payloads))
    }

}
