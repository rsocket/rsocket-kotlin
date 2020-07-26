package dev.whyoleg.rsocket

import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.payload.*
import kotlinx.coroutines.flow.*

interface RSocket : Cancelable {

    fun metadataPush(metadata: ByteArray): Unit = notImplemented("Metadata Push")

    fun fireAndForget(payload: Payload): Unit = notImplemented("Fire and Forget")

    suspend fun requestResponse(payload: Payload): Payload = notImplemented("Request Response")

    fun requestStream(payload: Payload): RequestingFlow<Payload> = notImplemented("Request Stream")

    fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> = notImplemented("Request Channel")
}

fun RSocket.requestChannel(
    payloads: Flow<Payload>,
    block: suspend (n: Int) -> Unit = {}
): RequestingFlow<Payload> = requestChannel(payloads.onRequest(block))

private fun notImplemented(operation: String): Nothing = throw NotImplementedError("$operation is not implemented.")
