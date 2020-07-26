package dev.whyoleg.rsocket.internal

import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.frame.*
import dev.whyoleg.rsocket.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

interface RSocketState : Cancelable {
    val streamIds: Map<Int, *>

    fun send(frame: Frame)
    fun sendPrioritized(frame: Frame)

    fun receiver(streamId: Int): ReceiveChannel<RequestFrame>

    suspend fun receiveOne(streamId: Int, receiver: ReceiveChannel<RequestFrame>): Payload
    suspend fun RequestingFlowCollector<Payload>.emitAll(streamId: Int, receiver: ReceiveChannel<RequestFrame>)

    fun RequestingFlow<Payload>.sendLimiting(streamId: Int, initialRequest: Int): ReceiveChannel<Payload>
    suspend fun CoroutineScope.sendStream(streamId: Int, stream: ReceiveChannel<Payload>)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job
    fun launchCancelable(streamId: Int, block: suspend CoroutineScope.() -> Unit): Job

    fun requestingFlow(block: suspend RequestingFlowCollector<Payload>.() -> Unit): RequestingFlow<Payload>

    fun start(requestHandler: RSocket): Job
}
