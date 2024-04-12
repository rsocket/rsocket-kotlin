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

package io.rsocket.kotlin.transport.nodejs.tcp

import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.nodejs.tcp.internal.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public sealed interface NodejsTcpServerInstance : RSocketServerInstance {
    public val host: String
    public val port: Int
}

public sealed interface NodejsTcpServerTransport : RSocketTransport {
    public fun target(host: String, port: Int): RSocketServerTarget<NodejsTcpServerInstance>

    public companion object Factory :
        RSocketTransportFactory<NodejsTcpServerTransport, NodejsTcpServerTransportBuilder>({ NodejsTcpServerTransportBuilderImpl })
}

public sealed interface NodejsTcpServerTransportBuilder : RSocketTransportBuilder<NodejsTcpServerTransport> {
    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)
}

private object NodejsTcpServerTransportBuilderImpl : NodejsTcpServerTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Default

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NodejsTcpServerTransport = NodejsTcpServerTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
    )
}

private class NodejsTcpServerTransportImpl(
    override val coroutineContext: CoroutineContext,
) : NodejsTcpServerTransport {
    override fun target(host: String, port: Int): RSocketServerTarget<NodejsTcpServerInstance> = NodejsTcpServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        host = host,
        port = port
    )
}

private class NodejsTcpServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val host: String,
    private val port: Int,
) : RSocketServerTarget<NodejsTcpServerInstance> {

    @RSocketTransportApi
    override suspend fun startServer(handler: RSocketConnectionHandler): NodejsTcpServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        val serverJob = launch {
            val handlerScope = CoroutineScope(coroutineContext.supervisorContext())
            val server = createServer(port, host, {
                coroutineContext.job.cancel("Server closed")
            }) {
                handlerScope.launch { handler.handleNodejsTcpConnection(it) }
            }
            try {
                awaitCancellation()
            } finally {
                suspendCoroutine { cont -> server.close { cont.resume(Unit) } }
            }
        }

        return NodejsTcpServerInstanceImpl(
            coroutineContext = coroutineContext + serverJob,
            host = host,
            port = port
        )
    }
}

@RSocketTransportApi
private class NodejsTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val host: String,
    override val port: Int,
) : NodejsTcpServerInstance
