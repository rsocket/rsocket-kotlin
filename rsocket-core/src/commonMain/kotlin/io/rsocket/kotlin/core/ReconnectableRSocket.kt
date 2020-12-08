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

package io.rsocket.kotlin.core

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal typealias ReconnectPredicate = suspend (cause: Throwable, attempt: Long) -> Boolean

@Suppress("FunctionName")
@OptIn(FlowPreview::class)
internal suspend fun ReconnectableRSocket(
    logger: Logger,
    connect: suspend () -> RSocket,
    predicate: ReconnectPredicate,
): RSocket {
    val job = Job()
    val state =
        connect.asFlow()
            .map<RSocket, ReconnectState> { ReconnectState.Connected(it) } //if connection established - state = connected
            .onStart { emit(ReconnectState.Connecting) } //init - state = connecting
            .retryWhen { cause, attempt -> //reconnection logic
                logger.debug(cause) { "Connection establishment failed, attempt: $attempt. Trying to reconnect..." }
                predicate(cause, attempt)
            }
            .catch { //reconnection failed - state = failed
                logger.debug(it) { "Reconnection failed" }
                emit(ReconnectState.Failed(it))
            }
            .transform { value ->
                emit(value) //emit before any action, to pass value directly to state

                when (value) {
                    is ReconnectState.Connected -> {
                        logger.debug { "Connection established" }
                        value.rSocket.join() //await for connection completion
                        logger.debug { "Connection closed. Reconnecting..." }
                    }
                    is ReconnectState.Failed    -> job.completeExceptionally(value.error) //reconnect failed, fail job
                    ReconnectState.Connecting   -> Unit //skip, still waiting for new connection
                }
            }
            .restarting() //reconnect if old connection completed
            .stateIn(CoroutineScope(Dispatchers.Unconfined + job))

    //await first connection to fail fast if something
    state.mapNotNull {
        when (it) {
            is ReconnectState.Connected -> it.rSocket
            is ReconnectState.Failed    -> throw it.error
            ReconnectState.Connecting   -> null
        }
    }.take(1).collect()

    return ReconnectableRSocket(job, state)
}

private fun Flow<ReconnectState>.restarting(): Flow<ReconnectState> = flow { while (true) emitAll(this@restarting) }

private sealed class ReconnectState {
    object Connecting : ReconnectState()
    data class Failed(val error: Throwable) : ReconnectState()
    data class Connected(val rSocket: RSocket) : ReconnectState()
}

private class ReconnectableRSocket(
    override val job: CompletableJob,
    private val state: StateFlow<ReconnectState>,
) : RSocket {

    private val reconnectHandler = state.mapNotNull { it.current() }.take(1)

    private suspend fun currentRSocket(closeable: Closeable): RSocket = closeable.closeOnError { currentRSocket() }

    private suspend fun currentRSocket(): RSocket = state.value.current() ?: reconnectHandler.first()

    private fun ReconnectState.current(): RSocket? = when (this) {
        is ReconnectState.Connected -> rSocket.takeIf(RSocket::isActive) //connection is ready to handle requests
        is ReconnectState.Failed    -> throw error //connection failed - fail requests
        ReconnectState.Connecting   -> null //reconnection
    }

    override suspend fun metadataPush(metadata: ByteReadPacket): Unit =
        currentRSocket(metadata).metadataPush(metadata)

    override suspend fun fireAndForget(payload: Payload): Unit =
        currentRSocket(payload).fireAndForget(payload)

    override suspend fun requestResponse(payload: Payload): Payload =
        currentRSocket(payload).requestResponse(payload)

    override fun requestStream(payload: Payload): Flow<Payload> = flow {
        emitAll(currentRSocket(payload).requestStream(payload))
    }

    override fun requestChannel(payloads: Flow<Payload>): Flow<Payload> = flow {
        emitAll(currentRSocket().requestChannel(payloads))
    }

}
