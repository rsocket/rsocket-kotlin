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

package io.rsocket.kotlin.internal

import io.rsocket.kotlin.*
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.flow.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@OptIn(
    InternalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    FlowPreview::class
)
internal class RSocketState(
    private val connection: Connection,
    keepAlive: KeepAlive,
    val requestStrategy: () -> RequestStrategy,
    val ignoredFrameConsumer: (Frame) -> Unit,
) : Cancelable by connection {
    private val prioritizer = Prioritizer()
    private val requestScope = CoroutineScope(SupervisorJob(job))
    private val scope = CoroutineScope(job)

    val receivers: MutableMap<Int, SendChannel<RequestFrame>> = concurrentMap()
    private val senders: MutableMap<Int, Job> = concurrentMap()
    private val limits: MutableMap<Int, LimitStrategy> = concurrentMap()

    private val keepAliveHandler = KeepAliveHandler(keepAlive, this::sendPrioritized)

    fun send(frame: Frame) {
        prioritizer.send(frame)
    }

    fun sendPrioritized(frame: Frame) {
        prioritizer.sendPrioritized(frame)
    }

    fun receiver(streamId: Int): ReceiveChannel<RequestFrame> {
        val receiver = Channel<RequestFrame>(Channel.UNLIMITED)
        receivers[streamId] = receiver
        return receiver
    }


    private suspend inline fun consumeCancelable(
        streamId: Int,
        receiver: ReceiveChannel<RequestFrame>,
        block: (frame: RequestFrame) -> Unit,
    ) {
        var cause: Throwable? = null
        try {
            for (e in receiver) block(e)
        } catch (e: Throwable) {
            cause = e
            throw e
        } finally {
            if (isActive && streamId in receivers) {
                if (cause != null) send(CancelFrame(streamId))
                receivers.remove(streamId)?.close(cause)
            }
        }
    }

    suspend fun receiveOne(streamId: Int, receiver: ReceiveChannel<RequestFrame>): Payload {
        consumeCancelable(streamId, receiver) { return it.payload }
        error("never happens") //TODO contract
    }

    suspend fun RequestingFlowCollector<Payload>.emitAll(streamId: Int, receiver: ReceiveChannel<RequestFrame>) {
        consumeCancelable(streamId, receiver) { frame ->
            if (frame.complete) return //TODO change, check next flag
            emit(frame.payload) { send(RequestNFrame(streamId, it)) }
        }
    }

    fun RequestingFlow<Payload>.sendLimiting(streamId: Int, initialRequest: Int): ReceiveChannel<Payload> {
        val strategy = LimitStrategy(initialRequest)
        limits[streamId] = strategy
        return requesting(strategy)/*.buffer(16)*/
            .onCompletion { if (isActive) limits -= streamId }
            .buffer(Channel.UNLIMITED)
            .produceIn(requestScope)
    }

    suspend fun CoroutineScope.sendStream(streamId: Int, stream: ReceiveChannel<Payload>) {
        try {
            stream.consumeEach {
                if (isActive) send(NextPayloadFrame(streamId, it))
                else return
            }
            if (isActive) send(CompletePayloadFrame(streamId))
        } catch (e: Throwable) {
            if (isActive) send(ErrorFrame(streamId, e))
            throw e
        }
    }

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = requestScope.launch(block = block)

    fun launchCancelable(streamId: Int, block: suspend CoroutineScope.() -> Unit): Job {
        val job = launch(block)
        job.invokeOnCompletion { if (isActive) senders -= streamId }
        senders[streamId] = job
        return job
    }

    fun requestingFlow(block: suspend RequestingFlowCollector<Payload>.() -> Unit): RequestingFlow<Payload> =
        RequestingFlow(requestStrategy, block)

    private fun handleFrame(responder: RSocketResponder, frame: Frame) {
        when (val streamId = frame.streamId) {
            0 -> when (frame) {
                is ErrorFrame        -> cancel("Zero stream error", frame.throwable)
                is KeepAliveFrame    -> keepAliveHandler.receive(frame)
                is LeaseFrame        -> error("lease isn't implemented")

                is MetadataPushFrame -> responder.handleMetadataPush(frame)
                else                 -> ignoredFrameConsumer(frame)
            }
            else -> when (frame) {
                is RequestNFrame -> limits[streamId]?.saveRequest(frame.requestN)
                is CancelFrame -> senders.remove(streamId)?.cancel()
                is ErrorFrame -> receivers.remove(streamId)?.close(frame.throwable)
                is RequestFrame -> when (frame.type) {
                    FrameType.Payload         -> receivers[streamId]?.offer(frame)
                    FrameType.RequestFnF      -> responder.handleFireAndForget(frame)
                    FrameType.RequestResponse -> responder.handlerRequestResponse(frame)
                    FrameType.RequestStream   -> responder.handleRequestStream(frame)
                    FrameType.RequestChannel  -> responder.handleRequestChannel(frame)
                    else                      -> error("never happens")
                }
                else             -> ignoredFrameConsumer(frame)
            }
        }
    }

    fun start(requestHandler: RSocket): Job {
        val responder = RSocketResponder(this, requestHandler)
        keepAliveHandler.startIn(scope)
        requestHandler.job.invokeOnCompletion { cancel("Request handled stopped", it) }
        job.invokeOnCompletion { error ->
            requestHandler.cancel("Connection closed", error)
            receivers.values.forEach { it.close((error as? CancellationException)?.cause ?: error) }
            receivers.clear()
            limits.clear()
            senders.clear()
            prioritizer.close(error)
        }
        scope.launch {
            while (connection.isActive) connection.send(prioritizer.receive().toPacket())
        }
        scope.launch {
            while (connection.isActive) handleFrame(responder, connection.receive().toFrame())
        }
        return job
    }
}

internal fun ReceiveChannel<*>.cancelConsumed(cause: Throwable?) {
    cancel(cause?.let { it as? CancellationException ?: CancellationException("Channel was consumed, consumer had failed", it) })
}
