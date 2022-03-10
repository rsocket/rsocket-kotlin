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

package io.rsocket.kotlin.connect

import io.rsocket.kotlin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

//TODO will be reworked when session state will be implemented

internal sealed class ReconnectState {
    object Connecting : ReconnectState()
    data class Failed(val error: Throwable) : ReconnectState()
    data class Connected(
        val connection: Connection,
        val requester: RSocket,
    ) : ReconnectState()
}

internal class ReconnectableRequester(
    private val state: StateFlow<ReconnectState>,
) : DelayedRequester() {

    override suspend fun get(): RSocket {
        return state.value.current() ?: state.mapNotNull { it.current() }.first()
    }

    private fun ReconnectState.current(): RSocket? = when (this) {
        is ReconnectState.Connected -> requester.takeIf { connection.isActive } //connection is ready to handle requests
        is ReconnectState.Failed    -> throw error //connection failed - fail requests
        ReconnectState.Connecting   -> null //reconnection
    }

}
