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
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.random.*

// TODO: rename to inprocess and more to another module/package later
public sealed interface LocalServerInstance : RSocketServerInstance {
    public val serverName: String
}

public sealed interface LocalServerTransport : RSocketTransport {
    public fun target(): RSocketServerTarget<LocalServerInstance>
    public fun target(serverName: String): RSocketServerTarget<LocalServerInstance>

    public companion object Factory :
        RSocketTransportFactory<LocalServerTransport, LocalServerTransportBuilder>(::LocalServerTransportBuilderImpl)
}

public sealed interface LocalServerTransportBuilder : RSocketTransportBuilder<LocalServerTransport> {
    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)

    public fun sequential(
        prioritizationQueueBuffersCapacity: Int = Channel.BUFFERED,
    )

    public fun multiplexed(
        streamsQueueCapacity: Int = Channel.BUFFERED,
        streamBufferCapacity: Int = Channel.BUFFERED,
    )
}

private class LocalServerTransportBuilderImpl : LocalServerTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Default
    private var connector: LocalServerConnector? = null

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    override fun sequential(prioritizationQueueBuffersCapacity: Int) {
        connector = LocalServerConnector.Sequential(prioritizationQueueBuffersCapacity)
    }

    override fun multiplexed(streamsQueueCapacity: Int, streamBufferCapacity: Int) {
        connector = LocalServerConnector.Multiplexed(streamsQueueCapacity, streamBufferCapacity)
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): LocalServerTransport = LocalServerTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
        connector = connector ?: LocalServerConnector.Sequential(Channel.BUFFERED)
    )
}

private class LocalServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val connector: LocalServerConnector,
) : LocalServerTransport {
    override fun target(serverName: String): RSocketServerTarget<LocalServerInstance> = LocalServerTargetImpl(
        serverName = serverName,
        coroutineContext = coroutineContext.supervisorContext(),
        connector = connector
    )

    @OptIn(ExperimentalStdlibApi::class)
    override fun target(): RSocketServerTarget<LocalServerInstance> = target(
        Random.nextBytes(16).toHexString(HexFormat.UpperCase)
    )
}

private class LocalServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val serverName: String,
    private val connector: LocalServerConnector,
) : RSocketServerTarget<LocalServerInstance> {
    @RSocketTransportApi
    override suspend fun startServer(handler: RSocketConnectionHandler): LocalServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        return LocalServerInstanceImpl(
            serverName = serverName,
            coroutineContext = coroutineContext.childContext(),
            serverHandler = handler,
            connector = connector
        )
    }
}
