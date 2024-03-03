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

// TODO: rename to inprocess and more to another module/package later
public sealed interface LocalServerInstance : RSocketServerInstance, LocalClientTarget

public sealed interface LocalServerTarget : RSocketServerTarget<LocalServerInstance> {
    public val serverName: String
}

public sealed interface LocalServerTransport : RSocketTransport<String, LocalServerTarget> {
    public fun target(): LocalServerTarget = target(LocalServerInstanceImpl.randomName())

    public companion object Factory : RSocketTransportFactory<
            String,
            LocalServerTarget,
            LocalServerTransport,
            LocalServerTransportBuilder>(::LocalServerTransportBuilderImpl)
}

public sealed interface LocalServerTransportBuilder : RSocketTransportBuilder<
        String,
        LocalServerTarget,
        LocalServerTransport> {

    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)
    public fun connectionBufferCapacity(capacity: Int)
}

private class LocalServerTransportBuilderImpl : LocalServerTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Default
    private var connectionBufferCapacity: Int = Channel.BUFFERED

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    override fun connectionBufferCapacity(capacity: Int) {
        this.connectionBufferCapacity = capacity
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): LocalServerTransport = LocalServerTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
        connectionBufferCapacity = connectionBufferCapacity
    )
}

private class LocalServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val connectionBufferCapacity: Int,
) : LocalServerTransport {
    override fun target(address: String): LocalServerTarget = LocalServerTargetImpl(
        serverName = address,
        coroutineContext = coroutineContext.supervisorContext(),
        connectionBufferCapacity = connectionBufferCapacity
    )
}

private class LocalServerTargetImpl(
    override val serverName: String,
    override val coroutineContext: CoroutineContext,
    private val connectionBufferCapacity: Int,
) : LocalServerTarget {
    @RSocketTransportApi
    override suspend fun startServer(acceptor: RSocketServerAcceptor): LocalServerInstance {
        ensureActive()

        return LocalServerInstanceImpl(
            serverName = serverName,
            coroutineContext = coroutineContext.supervisorContext(),
            connectionBufferCapacity = connectionBufferCapacity,
            acceptor = acceptor
        )
    }
}
