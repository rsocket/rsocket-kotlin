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

public sealed interface NodejsTcpClientTransport : RSocketTransport {
    public fun target(host: String, port: Int): RSocketClientTarget

    public companion object Factory :
        RSocketTransportFactory<NodejsTcpClientTransport, NodejsTcpClientTransportBuilder>(::NodejsTcpClientTransportBuilderImpl)
}

public sealed interface NodejsTcpClientTransportBuilder : RSocketTransportBuilder<NodejsTcpClientTransport> {
    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)
}

private class NodejsTcpClientTransportBuilderImpl : NodejsTcpClientTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Default

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NodejsTcpClientTransport = NodejsTcpClientTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
    )
}

private class NodejsTcpClientTransportImpl(
    override val coroutineContext: CoroutineContext,
) : NodejsTcpClientTransport {
    override fun target(host: String, port: Int): RSocketClientTarget = NodejsTcpClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        host = host,
        port = port
    )
}

private class NodejsTcpClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val host: String,
    private val port: Int,
) : RSocketClientTarget {
    @RSocketTransportApi
    override fun connectClient(handler: RSocketConnectionHandler): Job = launch {
        val socket = connect(port, host)
        handler.handleNodejsTcpConnection(socket)
    }
}
