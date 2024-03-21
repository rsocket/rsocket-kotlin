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

@OptIn(RSocketTransportApi::class)
public sealed interface LocalClientTransport : RSocketTransport {
    public fun target(serverName: String): RSocketClientTarget

    public companion object Factory :
        RSocketTransportFactory<LocalClientTransport, LocalClientTransportBuilder>(::LocalClientTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface LocalClientTransportBuilder : RSocketTransportBuilder<LocalClientTransport> {
    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)
}

private class LocalClientTransportBuilderImpl : LocalClientTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Default

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): LocalClientTransport = LocalClientTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
    )
}

private class LocalClientTransportImpl(
    override val coroutineContext: CoroutineContext,
) : LocalClientTransport {
    override fun target(serverName: String): RSocketClientTarget = LocalClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        server = LocalServerRegistry.get(serverName)
    )
}

@OptIn(RSocketTransportApi::class)
private class LocalClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val server: LocalServerInstanceImpl,
) : RSocketClientTarget {

    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job {
        coroutineContext.ensureActive()
        return server.connect(clientScope = this, clientHandler = handler)
    }
}
