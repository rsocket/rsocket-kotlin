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
        serverName: String,
        parentContext: CoroutineContext,
    ): RSocketConnection = get(serverName).connectClient(parentContext)

    fun startServer(
        serverName: String,
        parentContext: CoroutineContext,
        connector: LocalServerConnector,
        onConnection: (RSocketConnection) -> Unit,
    ): LocalServerInstance = LocalServerInstanceImpl(
        coroutineContext = parentContext.childContext(),
        serverName = serverName,
        connector = connector,
        onConnection = onConnection
    ).also {
        register(serverName, it)
    }
}

@RSocketTransportApi
private class LocalServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val serverName: String,
    private val connector: LocalServerConnector,
    private val onConnection: (RSocketConnection) -> Unit,
) : LocalServerInstance {
    private val serverContext = coroutineContext.supervisorContext()

    @RSocketTransportApi
    suspend fun connectClient(clientContext: CoroutineContext): RSocketConnection {
        coroutineContext.ensureActive()

        return connector.connect(
            clientContext = clientContext,
            serverContext = serverContext,
            onConnection = onConnection
        )
    }
}
