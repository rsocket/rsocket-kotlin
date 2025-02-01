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

package io.rsocket.kotlin.transport.local

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.atomicfu.locks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

@RSocketTransportApi
internal object LocalServerRegistry : SynchronizedObject() {
    private val instances = mutableMapOf<String, LocalServerInstanceImpl>()

    private fun register(name: String, instance: LocalServerInstanceImpl) {
        synchronized(this) {
            check(name !in instances) { "Already registered: $name" }
            instances[name] = instance
        }
        instance.coroutineContext.job.invokeOnCompletion {
            synchronized(this) {
                instances.remove(name)
            }
        }
    }

    private fun get(name: String): LocalServerInstanceImpl = synchronized(this) {
        checkNotNull(instances[name]) { "Cannot find $name" }
    }

    suspend fun connectClient(
        scope: CoroutineScope,
        serverName: String,
    ): RSocketConnection<LocalConnectionContext> = get(serverName).connect(scope)

    fun startServer(
        scope: CoroutineScope,
        serverName: String,
        connector: LocalServerConnector,
    ): RSocketServerInstance<LocalConnectionContext, LocalServerConfiguration> = LocalServerInstanceImpl(
        coroutineContext = scope.coroutineContext.supervisorContext(),
        configuration = LocalServerContext(serverName),
        connector = connector
    ).also {
        register(serverName, it)
    }
}

@RSocketTransportApi
private class LocalServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val configuration: LocalServerContext,
    private val connector: LocalServerConnector,
) : RSocketServerInstance<LocalConnectionContext, LocalServerConfiguration> {
    private val connections = Channel<RSocketConnection<LocalConnectionContext>>(Channel.BUFFERED) {
        it.cancel("Connection failed to be delivered to client")
    }

    init {
        coroutineContext.job.invokeOnCompletion {
            connections.cancel(CancellationException("Server stopped", it))
        }
    }

    override suspend fun acceptConnection(): RSocketConnection<LocalConnectionContext>? = connections.receiveCatching().getOrNull()

    @RSocketTransportApi
    suspend fun connect(clientScope: CoroutineScope): RSocketConnection<LocalConnectionContext> {
        coroutineContext.ensureActive()

        return connector.connect(
            connectionContext = configuration,
            serverConnections = connections,
            clientScope = clientScope,
            serverScope = this
        )
    }
}

private class LocalServerContext(
    override val serverName: String,
) : LocalConnectionContext, LocalServerConfiguration
