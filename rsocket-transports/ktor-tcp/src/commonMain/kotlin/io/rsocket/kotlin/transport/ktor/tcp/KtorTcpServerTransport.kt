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

public sealed interface KtorTcpServerInstance : RSocketServerInstance {
    public val localAddress: SocketAddress
}

public sealed interface KtorTcpServerTransport : RSocketTransport {
    public fun target(localAddress: SocketAddress? = null): RSocketServerTarget<KtorTcpServerInstance>
    public fun target(host: String = "0.0.0.0", port: Int = 0): RSocketServerTarget<KtorTcpServerInstance>

    public companion object Factory :
        RSocketTransportFactory<KtorTcpServerTransport, KtorTcpServerTransportBuilder>(::KtorTcpServerTransportBuilderImpl)
}

public sealed interface KtorTcpServerTransportBuilder : RSocketTransportBuilder<KtorTcpServerTransport> {
    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)

    public fun selectorManagerDispatcher(context: CoroutineContext)
    public fun selectorManager(manager: SelectorManager, manage: Boolean)

    public fun socketOptions(block: SocketOptions.AcceptorOptions.() -> Unit)
}

private class KtorTcpServerTransportBuilderImpl : KtorTcpServerTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.IO
    private var selector: KtorTcpSelector? = null
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
    override fun target(localAddress: SocketAddress?): RSocketServerTarget<KtorTcpServerInstance> = KtorTcpServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        socketOptions = socketOptions,
        selectorManager = selectorManager,
        localAddress = localAddress
    )

    override fun target(host: String, port: Int): RSocketServerTarget<KtorTcpServerInstance> = target(InetSocketAddress(host, port))
}

private class KtorTcpServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    private val socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
    private val selectorManager: SelectorManager,
    private val localAddress: SocketAddress?,
) : RSocketServerTarget<KtorTcpServerInstance> {

    @RSocketTransportApi
    override suspend fun startServer(handler: RSocketConnectionHandler): KtorTcpServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()
        return startKtorTcpServer(this, bindSocket(), handler)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun bindSocket(): ServerSocket = launchCoroutine { cont ->
        val socket = aSocket(selectorManager).tcp().bind(localAddress, socketOptions)
        cont.resume(socket) { socket.close() }
    }
}

@RSocketTransportApi
private fun startKtorTcpServer(
    scope: CoroutineScope,
    serverSocket: ServerSocket,
    handler: RSocketConnectionHandler,
): KtorTcpServerInstance {
    val serverJob = scope.launch {
        try {
            // the failure of one connection should not stop all other connections
            supervisorScope {
                while (true) {
                    val socket = serverSocket.accept()
                    launch { handler.handleKtorTcpConnection(socket) }
                }
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
        localAddress = serverSocket.localAddress
    )
}

@RSocketTransportApi
private class KtorTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    override val localAddress: SocketAddress,
) : KtorTcpServerInstance
