package dev.whyoleg.rsocket.internal

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.frame.*
import dev.whyoleg.rsocket.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class RSocketRequester(
    state: RSocketState,
    private val streamId: StreamId
) : RSocket, RSocketState by state {
    private fun nextStreamId(): Int = streamId.next(streamIds)

    override fun metadataPush(metadata: ByteArray) {
        checkAvailable()
        sendPrioritized(MetadataPushFrame(metadata))
    }

    override fun fireAndForget(payload: Payload) {
        checkAvailable()
        send(RequestFireAndForgetFrame(nextStreamId(), payload))
    }

    override suspend fun requestResponse(payload: Payload): Payload {
        checkAvailable()
        val streamId = nextStreamId()
        val receiver = receiver(streamId)
        send(RequestResponseFrame(streamId, payload))
        return receiveOne(streamId, receiver)
    }

    override fun requestStream(payload: Payload): RequestingFlow<Payload> = requestingFlow {
        checkAvailable()
        val streamId = nextStreamId()
        val receiver = receiver(streamId)
        send(RequestStreamFrame(streamId, initialRequest, payload))
        emitAll(streamId, receiver)
    }

    override fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> = requestingFlow {
        checkAvailable()
        val streamId = nextStreamId()
        val request = payloads.sendLimiting(streamId, 1)
        val firstPayload = request.firstOrNull() ?: return@requestingFlow
        val receiver = receiver(streamId)
        send(RequestChannelFrame(streamId, initialRequest, firstPayload))
        launchCancelable(streamId) {
            sendStream(streamId, request)
        }.invokeOnCompletion {
            if (it != null && it !is CancellationException) receiver.cancelConsumed(it)
        }
        try {
            emitAll(streamId, receiver)
        } catch (e: Throwable) {
            request.cancelConsumed(e)
            throw e
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun ReceiveChannel<Payload>.firstOrNull(): Payload? {
        try {
            val value = receiveOrNull()
            if (value == null) cancel()
            return value
        } catch (e: Throwable) {
            cancelConsumed(e)
            throw e
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun checkAvailable() {
        if (isActive) return
        val error = job.getCancellationException()
        throw error.cause ?: error
    }

}
