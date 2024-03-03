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

public sealed interface NodejsTcpClientTarget : RSocketClientTarget {
    public val address: NodejsTcpAddress
}

public sealed interface NodejsTcpClientTransport : RSocketTransport<
        NodejsTcpAddress,
        NodejsTcpClientTarget> {

    public fun target(hostname: String, port: Int): NodejsTcpClientTarget = target(NodejsTcpAddress(hostname, port))

    public companion object Factory : RSocketTransportFactory<
            NodejsTcpAddress,
            NodejsTcpClientTarget,
            NodejsTcpClientTransport,
            NodejsTcpClientTransportBuilder>({ NodejsTcpClientTransportBuilderImpl })
}

public sealed interface NodejsTcpClientTransportBuilder : RSocketTransportBuilder<
        NodejsTcpAddress,
        NodejsTcpClientTarget,
        NodejsTcpClientTransport>

private object NodejsTcpClientTransportBuilderImpl : NodejsTcpClientTransportBuilder {
    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): NodejsTcpClientTransport = NodejsTcpClientTransportImpl(
        coroutineContext = context.supervisorContext(),
    )
}

private class NodejsTcpClientTransportImpl(
    override val coroutineContext: CoroutineContext,
) : NodejsTcpClientTransport {

    override fun target(address: NodejsTcpAddress): NodejsTcpClientTarget = NodejsTcpClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        address = address
    )
}

private class NodejsTcpClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val address: NodejsTcpAddress,
) : NodejsTcpClientTarget {

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession {
        ensureActive()

        return NodejsTcpSession(coroutineContext.childContext(), connect(address.port, address.hostname))
    }
}
