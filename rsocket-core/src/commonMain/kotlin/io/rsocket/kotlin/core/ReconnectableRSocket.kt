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
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal typealias ReconnectPredicate = suspend (cause: Throwable, attempt: Long) -> Boolean

@Suppress("FunctionName")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal suspend fun ReconnectableRSocket(
    connect: suspend () -> RSocket,
    predicate: ReconnectPredicate,
): RSocket {
    val state = MutableStateFlow<ReconnectState>(ReconnectState.Connecting)

    val job =
        connect.asFlow()
            .map<RSocket, ReconnectState> { ReconnectState.Connected(it) } //if connection established - state = connected
            .onStart { emit(ReconnectState.Connecting) } //init - state = connecting
            .retryWhen { cause, attempts -> predicate(cause, attempts) } //reconnection logic
            .catch { emit(ReconnectState.Failed(it)) } //reconnection failed - state = failed
            .mapNotNull {
                state.value = it //set state //TODO replace with Flow.stateIn when coroutines 1.4.0 will be released

                when (it) {
                    is ReconnectState.Connected -> it.rSocket.join()  //await for connection completion
                    is ReconnectState.Failed    -> throw it.error //reconnect failed, cancel job
                    ReconnectState.Connecting   -> null //ignore, should never happen
                }
            }
            .launchRestarting() //reconnect if old connection completed/failed

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

private fun Flow<*>.launchRestarting(): Job = GlobalScope.launch(Dispatchers.Unconfined) {
    while (isActive) {
        try {
            collect()
        } catch (e: Throwable) {
            // KLUDGE: K/N
            cancel("Reconnect failed", e)
            break
        }
    }
}

private sealed class ReconnectState {
    object Connecting : ReconnectState()
    data class Failed(val error: Throwable) : ReconnectState()
    data class Connected(val rSocket: RSocket) : ReconnectState()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
private class ReconnectableRSocket(
    override val job: Job,
    private val state: StateFlow<ReconnectState>,
) : RSocket {

    private val reconnectHandler = state.mapNotNull { it.handleState { null } }.take(1)

    //null pointer will never happen
    private suspend fun currentRSocket(): RSocket = state.value.handleState { reconnectHandler.first() }!!

    private inline fun ReconnectState.handleState(onReconnect: () -> RSocket?): RSocket? = when (this) {
        is ReconnectState.Connected -> when {
            rSocket.isActive -> rSocket //connection is ready to handle requests
            else             -> onReconnect() //reconnection
        }
        is ReconnectState.Failed    -> throw error //connection failed - fail requests
        ReconnectState.Connecting   -> onReconnect() //reconnection
    }

    private suspend inline fun <T : Any> execSuspend(operation: RSocket.() -> T): T =
        currentRSocket().operation()

    private inline fun execFlow(crossinline operation: RSocket.() -> Flow<Payload>): Flow<Payload> =
        flow { emitAll(currentRSocket().operation()) }

    override suspend fun metadataPush(metadata: ByteReadPacket): Unit = execSuspend { metadataPush(metadata) }
    override suspend fun fireAndForget(payload: Payload): Unit = execSuspend { fireAndForget(payload) }
    override suspend fun requestResponse(payload: Payload): Payload = execSuspend { requestResponse(payload) }
    override fun requestStream(payload: Payload): Flow<Payload> = execFlow { requestStream(payload) }
    override fun requestChannel(payloads: Flow<Payload>): Flow<Payload> = execFlow { requestChannel(payloads) }

}
