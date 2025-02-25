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
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.uuid.*

@OptIn(RSocketTransportApi::class)
public sealed interface LocalServerInstance : RSocketServerInstance {
    public val serverName: String
}

public typealias LocalServerTarget = RSocketServerTarget<LocalServerInstance>

@OptIn(RSocketTransportApi::class)
public sealed interface LocalServerTransport : RSocketTransport {
    public fun target(): LocalServerTarget
    public fun target(serverName: String): LocalServerTarget

    public companion object Factory :
        RSocketTransportFactory<LocalServerTransport, LocalServerTransportBuilder>(::LocalServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface LocalServerTransportBuilder : RSocketTransportBuilder<LocalServerTransport> {
    public fun dispatcher(context: CoroutineContext)

    public fun sequential()
    public fun multiplexed()
}

private class LocalServerTransportBuilderImpl : LocalServerTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Unconfined
    private var connector: LocalServerConnector? = null

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    override fun sequential() {
        connector = LocalServerConnector.Sequential
    }

    override fun multiplexed() {
        connector = LocalServerConnector.Multiplexed
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): LocalServerTransport = LocalServerTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
        connector = connector ?: LocalServerConnector.Sequential
    )
}

private class LocalServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val connector: LocalServerConnector,
) : LocalServerTransport {
    override fun target(serverName: String): LocalServerTarget = LocalServerTargetImpl(
        serverName = serverName,
        coroutineContext = coroutineContext.supervisorContext(),
        connector = connector
    )

    @OptIn(ExperimentalUuidApi::class)
    override fun target(): LocalServerTarget = target(Uuid.random().toString())
}

@OptIn(RSocketTransportApi::class)
private class LocalServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val serverName: String,
    private val connector: LocalServerConnector,
) : LocalServerTarget {
    @RSocketTransportApi
    override suspend fun startServer(onConnection: (RSocketConnection) -> Unit): LocalServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        return LocalServerRegistry.startServer(serverName, coroutineContext, connector, onConnection)
    }
}
