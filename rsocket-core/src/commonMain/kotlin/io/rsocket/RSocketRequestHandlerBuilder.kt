package io.rsocket

import io.rsocket.flow.*
import io.rsocket.payload.*
import kotlinx.coroutines.*

class RSocketRequestHandlerBuilder internal constructor(private val job: Job) {
    var metadataPush: (RSocket.(metadata: ByteArray) -> Unit)? = null
    var fireAndForget: (RSocket.(payload: Payload) -> Unit)? = null
    var requestResponse: (suspend RSocket.(payload: Payload) -> Payload)? = null
    var requestStream: (RSocket.(payload: Payload) -> RequestingFlow<Payload>)? = null
    var requestChannel: (RSocket.(payloads: RequestingFlow<Payload>) -> RequestingFlow<Payload>)? = null

    internal fun build(): RSocket = RSocketRequestHandler(job, metadataPush, fireAndForget, requestResponse, requestStream, requestChannel)
}

@Suppress("FunctionName")
fun RSocketRequestHandler(parentJon: Job? = null, configure: RSocketRequestHandlerBuilder.() -> Unit): RSocket {
    val builder = RSocketRequestHandlerBuilder(Job(parentJon))
    builder.configure()
    return builder.build()
}

private class RSocketRequestHandler(
    override val job: Job,
    private val metadataPush: (RSocket.(metadata: ByteArray) -> Unit)? = null,
    private val fireAndForget: (RSocket.(payload: Payload) -> Unit)? = null,
    private val requestResponse: (suspend RSocket.(payload: Payload) -> Payload)? = null,
    private val requestStream: (RSocket.(payload: Payload) -> RequestingFlow<Payload>)? = null,
    private val requestChannel: (RSocket.(payloads: RequestingFlow<Payload>) -> RequestingFlow<Payload>)? = null
) : RSocket {
    override fun metadataPush(metadata: ByteArray): Unit =
        metadataPush?.invoke(this, metadata) ?: super.metadataPush(metadata)

    override fun fireAndForget(payload: Payload): Unit =
        fireAndForget?.invoke(this, payload) ?: super.fireAndForget(payload)

    override suspend fun requestResponse(payload: Payload): Payload =
        requestResponse?.invoke(this, payload) ?: super.requestResponse(payload)

    override fun requestStream(payload: Payload): RequestingFlow<Payload> =
        requestStream?.invoke(this, payload) ?: super.requestStream(payload)

    override fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> =
        requestChannel?.invoke(this, payloads) ?: super.requestChannel(payloads)

}
