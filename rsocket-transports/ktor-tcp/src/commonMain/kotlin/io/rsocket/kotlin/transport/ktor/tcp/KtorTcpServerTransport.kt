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
    public val localAddress: InetSocketAddress
}

public sealed interface KtorTcpServerTarget : RSocketServerTarget<KtorTcpServerInstance> {
    public val localAddress: InetSocketAddress?
}

public sealed interface KtorTcpServerTransport : RSocketTransport<
        InetSocketAddress?,
        KtorTcpServerTarget> {

    public fun target(): KtorTcpServerTarget = target(null)
    public fun target(hostname: String = "0.0.0.0", port: Int = 0): KtorTcpServerTarget = target(InetSocketAddress(hostname, port))

    public companion object Factory : RSocketTransportFactory<
            InetSocketAddress?,
            KtorTcpServerTarget,
            KtorTcpServerTransport,
            KtorTcpServerTransportBuilder>(::KtorTcpServerTransportBuilderImpl)
}

public sealed interface KtorTcpServerTransportBuilder : RSocketTransportBuilder<
        InetSocketAddress?,
        KtorTcpServerTarget,
        KtorTcpServerTransport> {

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
    override fun buildTransport(context: CoroutineContext): KtorTcpServerTransport = KtorTcpServerTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
        socketOptions = socketOptions,
        selector = selector
    )
}

private class KtorTcpServerTransportImpl private constructor(
    override val coroutineContext: CoroutineContext,
    private val socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
    private val selectorManager: SelectorManager,
) : KtorTcpServerTransport {
    constructor(
        coroutineContext: CoroutineContext,
        socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
        selector: KtorTcpSelector?,
    ) : this(coroutineContext, socketOptions, selector.createFor(coroutineContext))

    override fun target(address: InetSocketAddress?): KtorTcpServerTarget = KtorTcpServerTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        localAddress = address,
        socketOptions = socketOptions,
        selectorManager = selectorManager
    )
}

private class KtorTcpServerTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val localAddress: InetSocketAddress?,
    private val socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
    private val selectorManager: SelectorManager,
) : KtorTcpServerTarget {

    @RSocketTransportApi
    override suspend fun startServer(acceptor: RSocketServerAcceptor): KtorTcpServerInstance {
        return launchCont {
            val serverSocket = aSocket(selectorManager).tcp().bind(localAddress, socketOptions)
            KtorTcpServerInstanceImpl(
                coroutineContext = coroutineContext.supervisorContext(),
                serverSocket = serverSocket,
                acceptor = acceptor
            )
        }
    }
}

@RSocketTransportApi
private class KtorTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    private val serverSocket: ServerSocket,
    private val acceptor: RSocketServerAcceptor,
) : KtorTcpServerInstance {
    override val localAddress: InetSocketAddress get() = serverSocket.localAddress as InetSocketAddress

    init {
        launch {
            supervisorScope {
                while (true) {
                    val clientSocket = serverSocket.accept()
                    launch {
                        acceptor.acceptSession(
                            KtorTcpSession(
                                coroutineContext = coroutineContext.childContext(),
                                socket = clientSocket
                            )
                        )
                    }
                }
            }
        }.invokeOnCompletion { serverSocket.close() }
    }
}
