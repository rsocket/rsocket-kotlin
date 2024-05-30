/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.transport.local

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class LocalServerInstanceImpl @RSocketTransportApi constructor(
    override val serverName: String,
    override val coroutineContext: CoroutineContext,
    private val serverHandler: RSocketConnectionHandler,
    private val connector: LocalServerConnector,
) : LocalServerInstance {
    private val serverScope = CoroutineScope(coroutineContext.supervisorContext())

    init {
        LocalServerRegistry.register(serverName, this)
    }

    @RSocketTransportApi
    fun connect(
        clientScope: CoroutineScope,
        clientHandler: RSocketConnectionHandler,
    ): Job {
        coroutineContext.ensureActive()

        return connector.connect(
            clientScope = clientScope,
            clientHandler = clientHandler,
            serverScope = serverScope,
            serverHandler = serverHandler
        )
    }
}
