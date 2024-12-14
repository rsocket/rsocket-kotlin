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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public class KtorTcpServerConfiguration internal constructor(
    public val localAddress: SocketAddress,
)

public typealias KtorTcpServerTarget = RSocketServerTarget<KtorTcpConnectionContext, KtorTcpServerConfiguration>

@OptIn(RSocketTransportApi::class)
public sealed interface KtorTcpServerTransport : RSocketTransport {
    public fun target(localAddress: SocketAddress? = null): KtorTcpServerTarget
    public fun target(host: String = "0.0.0.0", port: Int = 0): KtorTcpServerTarget

    public companion object Factory :
        RSocketTransportFactory<KtorTcpServerTransport, KtorTcpServerTransportBuilder>(::KtorTcpServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface KtorTcpServerTransportBuilder : RSocketTransportBuilder<KtorTcpServerTransport> {
    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)

    public fun selectorManagerDispatcher(context: CoroutineContext)
    public fun selectorManager(manager: SelectorManager, manage: Boolean)

    public fun socketOptions(block: SocketOptions.AcceptorOptions.() -> Unit)
}

private class KtorTcpServerTransportBuilderImpl : KtorTcpServerTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.Default
    private var selector: KtorTcpSelector = KtorTcpSelector.FromContext(Dispatchers.IO)
    private var socketOptions: SocketOptions.AcceptorOptions.() -> Unit = {}

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    override fun socketOptions(block: SocketOptions.AcceptorOptions.() -> Unit) {
        this.socketOptions = block
    }

    override fun selectorManagerDispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.selector = KtorTcpSelector.FromContext(context)
    }

    override fun selectorManager(manager: SelectorManager, manage: Boolean) {
        this.selector = KtorTcpSelector.FromInstance(manager, manage)
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): KtorTcpServerTransport {
        val transportContext = context.supervisorContext() + dispatcher
        return KtorTcpServerTransportImpl(
            coroutineContext = transportContext,
            socketOptions = socketOptions,
            selectorManager = selector.createFor(transportContext)
        )
    }
}

private class KtorTcpServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
    private val selectorManager: SelectorManager,
) : KtorTcpServerTransport {
    override fun target(localAddress: SocketAddress?): KtorTcpServerTarget = KtorTcpServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        socketOptions = socketOptions,
        selectorManager = selectorManager,
        localAddress = localAddress
    )

    override fun target(host: String, port: Int): KtorTcpServerTarget = target(InetSocketAddress(host, port))
}

@OptIn(RSocketTransportApi::class)
private class KtorTcpServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
    private val selectorManager: SelectorManager,
    private val localAddress: SocketAddress?,
) : KtorTcpServerTarget {
    @RSocketTransportApi
    override suspend fun startServer(inbound: RSocketServerInstance.Inbound<KtorTcpConnectionContext>): RSocketServerInstance<KtorTcpServerConfiguration> {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()
        return startKtorTcpServer(this, aSocket(selectorManager).tcp().bind(localAddress, socketOptions), inbound)
    }
}

@RSocketTransportApi
private fun startKtorTcpServer(
    scope: CoroutineScope,
    serverSocket: ServerSocket,
    inbound: RSocketServerInstance.Inbound<KtorTcpConnectionContext>,
): RSocketServerInstance<KtorTcpServerConfiguration> {
    val serverJob = scope.launch {
        try {
            // the failure of one connection should not stop all other connections
            // TODO: supervisorScope is not needed
            supervisorScope {
                while (true) inbound.onConnection(
                    KtorTcpConnection(
                        parentScope = this@supervisorScope,
                        socket = serverSocket.accept()
                    )
                )
            }
        } finally {
            // even if it was cancelled, we still need to close socket and await it closure
            withContext(NonCancellable) {
                serverSocket.close()
                serverSocket.socketContext.join()
            }
        }
    }
    return KtorTcpServerInstanceImpl(
        coroutineContext = scope.coroutineContext + serverJob,
        configuration = KtorTcpServerConfiguration(serverSocket.localAddress)
    )
}

@RSocketTransportApi
private class KtorTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val configuration: KtorTcpServerConfiguration,
) : RSocketServerInstance<KtorTcpServerConfiguration>
