package io.rsocket.internal

import io.rsocket.*
import io.rsocket.frame.*
import kotlinx.coroutines.*

internal class RSocketResponder(
    state: RSocketState,
    private val requestHandler: RSocket
) : RSocketState by state {

    fun handleMetadataPush(frame: MetadataPushFrame) {
        launch {
            requestHandler.metadataPush(frame.metadata)
        }
    }

    fun handleFireAndForget(frame: RequestFrame) {
        launch {
            requestHandler.fireAndForget(frame.payload)
        }
    }

    fun handlerRequestResponse(frame: RequestFrame) {
        val streamId = frame.streamId
        launchCancelable(streamId) {
            val response = requestOrThrow(streamId) {
                requestHandler.requestResponse(frame.payload)
            }
            if (isActive) send(NextCompletePayloadFrame(streamId, response))
        }
    }

    fun handleRequestStream(initFrame: RequestFrame) {
        val streamId = initFrame.streamId
        launchCancelable(streamId) {
            val response = requestOrThrow(streamId) {
                requestHandler.requestStream(initFrame.payload)
            }
            sendStream(streamId, response.sendLimiting(streamId, initFrame.initialRequest))
        }
    }

    fun handleRequestChannel(initFrame: RequestFrame) {
        val streamId = initFrame.streamId
        val receiver = receiver(streamId)
        //TODO prevent consuming more then one time
        val request = requestingFlow {
            emit(initFrame.payload)
            if (initialRequest > 1) send(RequestNFrame(streamId, initialRequest - 1)) //-1 because first payload received
            emitAll(streamId, receiver)
        }
        launchCancelable(streamId) {
            val response = requestOrThrow(streamId) {
                requestHandler.requestChannel(request)
            }
            sendStream(streamId, response.sendLimiting(streamId, initFrame.initialRequest))
        }.invokeOnCompletion {
            if (it != null) receiver.cancelConsumed(it) //TODO check it
        }
    }

    private inline fun <T> CoroutineScope.requestOrThrow(streamId: Int, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            if (isActive) send(ErrorFrame(streamId, e))
            throw e
        }
    }

}
