package io.rsocket.internal

import io.rsocket.*
import io.rsocket.connection.*
import io.rsocket.flow.*
import io.rsocket.frame.*
import io.rsocket.keepalive.*
import io.rsocket.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@OptIn(
    InternalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    FlowPreview::class
)
internal class RSocketStateImpl(
    private val connection: Connection,
    keepAlive: KeepAlive,
    val requestStrategy: () -> RequestStrategy,
    val ignoredFrameConsumer: (Frame) -> Unit
) : RSocketState, Cancelable by connection {
    private val prioritizer = Prioritizer()
    private val requestScope = CoroutineScope(SupervisorJob(job))
    private val scope = CoroutineScope(job)

    private val receivers: MutableMap<Int, SendChannel<RequestFrame>> = concurrentMap()
    private val senders: MutableMap<Int, Job> = concurrentMap()
    private val limits: MutableMap<Int, LimitStrategy> = concurrentMap()
    override val streamIds: Map<Int, *> get() = receivers

    private val keepAliveHandler = KeepAliveHandler(keepAlive, this::sendPrioritized)

    override fun send(frame: Frame) {
        prioritizer.send(frame)
    }

    override fun sendPrioritized(frame: Frame) {
        prioritizer.sendPrioritized(frame)
    }

    override fun receiver(streamId: Int): ReceiveChannel<RequestFrame> {
        val receiver = Channel<RequestFrame>(Channel.UNLIMITED)
        receivers[streamId] = receiver
        return receiver
    }


    private suspend inline fun consumeCancelable(
        streamId: Int,
        receiver: ReceiveChannel<RequestFrame>,
        block: (frame: RequestFrame) -> Unit
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

    override suspend fun receiveOne(streamId: Int, receiver: ReceiveChannel<RequestFrame>): Payload {
        consumeCancelable(streamId, receiver) { return it.payload }
        error("never happens") //TODO contract
    }

    override suspend fun RequestingFlowCollector<Payload>.emitAll(streamId: Int, receiver: ReceiveChannel<RequestFrame>) {
        consumeCancelable(streamId, receiver) { frame ->
            if (frame.complete) return //TODO change, check next flag
            emit(frame.payload) { send(RequestNFrame(streamId, it)) }
        }
    }

    override fun RequestingFlow<Payload>.sendLimiting(streamId: Int, initialRequest: Int): ReceiveChannel<Payload> {
        val strategy = LimitStrategy(initialRequest)
        limits[streamId] = strategy
        return requesting(strategy)/*.buffer(16)*/
            .onCompletion { if (isActive) limits -= streamId }
            .buffer(Channel.UNLIMITED)
            .produceIn(requestScope)
    }

    override suspend fun CoroutineScope.sendStream(streamId: Int, stream: ReceiveChannel<Payload>) {
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

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job = requestScope.launch(block = block)

    override fun launchCancelable(streamId: Int, block: suspend CoroutineScope.() -> Unit): Job {
        val job = launch(block)
        job.invokeOnCompletion { if (isActive) senders -= streamId }
        senders[streamId] = job
        return job
    }

    override fun requestingFlow(block: suspend RequestingFlowCollector<Payload>.() -> Unit): RequestingFlow<Payload> =
        RequestingFlow(requestStrategy, block)

    private fun handleFrame(responder: RSocketResponder, frame: Frame) {
        when (val streamId = frame.streamId) {
            0 -> when (frame) {
                is ErrorFrame -> cancel("Zero stream error", frame.throwable)
                is KeepAliveFrame -> keepAliveHandler.receive(frame)
                is LeaseFrame -> error("lease isn't implemented")

                is MetadataPushFrame -> responder.handleMetadataPush(frame)
                else                 -> ignoredFrameConsumer(frame)
            }
            else -> when (frame) {
                is RequestNFrame -> limits[streamId]?.saveRequest(frame.requestN)
                is CancelFrame -> senders.remove(streamId)?.cancel()
                is ErrorFrame -> receivers.remove(streamId)?.close(frame.throwable)
                is RequestFrame -> when (frame.type) {
                    FrameType.Payload -> receivers[streamId]?.offer(frame)
                    FrameType.RequestFnF -> responder.handleFireAndForget(frame)
                    FrameType.RequestResponse -> responder.handlerRequestResponse(frame)
                    FrameType.RequestStream -> responder.handleRequestStream(frame)
                    FrameType.RequestChannel -> responder.handleRequestChannel(frame)
                    else                      -> error("never happens")
                }
                else             -> ignoredFrameConsumer(frame)
            }
        }
    }

    override fun start(requestHandler: RSocket): Job {
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
            while (connection.isActive) connection.send(prioritizer.receive().toByteArray())
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
