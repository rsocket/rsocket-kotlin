/*
 * Copyright 2015-2025 the original author or authors.
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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.*
import kotlin.coroutines.*

internal typealias ReconnectPredicate = suspend (cause: Throwable, attempt: Long) -> Boolean

@OptIn(RSocketLoggingApi::class)
internal suspend fun connectWithReconnect(
    coroutineContext: CoroutineContext,
    logger: Logger,
    connect: suspend () -> RSocket,
    predicate: ReconnectPredicate,
): RSocket {
    val child = Job(coroutineContext[Job])
    val childContext = coroutineContext + child
    val state = flow {
        emit(ReconnectState.Connecting) //init - state = connecting
        val rSocket = connect()
        emit(ReconnectState.Connected(rSocket)) //if connection established - state = connected
    }.retryWhen { cause, attempt -> //reconnection logic
        logger.debug(cause) { "Connection establishment failed, attempt: $attempt. Trying to reconnect..." }
        predicate(cause, attempt)
    }.catch { //reconnection failed - state = failed
        logger.debug(it) { "Reconnection failed" }
        emit(ReconnectState.Failed(it))
    }.transform { value ->
        emit(value) //emit before any action, to pass value directly to state

        when (value) {
            is ReconnectState.Connected -> {
                logger.debug { "Connection established" }
                value.rSocket.coroutineContext.job.join() //await for connection completion
                logger.debug { "Connection closed. Reconnecting..." }
            }

            is ReconnectState.Failed    -> child.cancel("Reconnect failed", value.error) //reconnect failed, fail job
            ReconnectState.Connecting   -> Unit //skip, still waiting for new connection
        }
    }.restarting() //reconnect if old connection completed
        .stateIn(CoroutineScope(childContext), SharingStarted.Eagerly, ReconnectState.Connecting)

    return ReconnectableRSocket(childContext, state).apply {
        //await first connection to fail fast if something
        try {
            currentRSocket()
        } catch (error: Throwable) {
            child.cancel() //if during connecting, cancelled from user side
            throw error
        }
    }
}

private fun Flow<ReconnectState>.restarting(): Flow<ReconnectState> = flow { while (true) emitAll(this@restarting) }

private sealed class ReconnectState {
    object Connecting : ReconnectState()
    data class Failed(val error: Throwable) : ReconnectState()
    data class Connected(val rSocket: RSocket) : ReconnectState()
}

private class ReconnectableRSocket(
    override val coroutineContext: CoroutineContext,
    private val state: StateFlow<ReconnectState>,
) : RSocket {

    suspend fun currentRSocket(): RSocket = state.value.current() ?: state.mapNotNull { it.current() }.first()

    private suspend inline fun currentRSocket(onFailure: () -> Unit): RSocket = try {
        currentRSocket()
    } catch (cause: Throwable) {
        onFailure()
        throw cause
    }

    private fun ReconnectState.current(): RSocket? = when (this) {
        is ReconnectState.Connected -> rSocket.takeIf(RSocket::isActive) //connection is ready to handle requests
        is ReconnectState.Failed    -> throw error //connection failed - fail requests
        ReconnectState.Connecting   -> null //reconnection
    }

    override suspend fun metadataPush(metadata: Buffer): Unit =
        currentRSocket(metadata::clear).metadataPush(metadata)

    override suspend fun fireAndForget(payload: Payload): Unit =
        currentRSocket(payload::close).fireAndForget(payload)

    override suspend fun requestResponse(payload: Payload): Payload =
        currentRSocket(payload::close).requestResponse(payload)

    override fun requestStream(payload: Payload): Flow<Payload> = flow {
        emitAll(currentRSocket(payload::close).requestStream(payload))
    }

    override fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload> = flow {
        emitAll(currentRSocket(initPayload::close).requestChannel(initPayload, payloads))
    }

}
