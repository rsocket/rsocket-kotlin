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

package io.rsocket.kotlin.transport.ktor.tcp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(RSocketTransportApi::class)
public sealed interface KtorTcpServerInstance : RSocketServerInstance {
    public val localAddress: SocketAddress
}

public typealias KtorTcpServerTarget = RSocketServerTarget<KtorTcpServerInstance>

@OptIn(RSocketTransportApi::class)
public sealed interface KtorTcpServerTransport : RSocketTransport {
    public fun target(localAddress: SocketAddress? = null): KtorTcpServerTarget
    public fun target(host: String = "0.0.0.0", port: Int = 0): KtorTcpServerTarget

    public companion object Factory :
        RSocketTransportFactory<KtorTcpServerTransport, KtorTcpServerTransportBuilder>(::KtorTcpServerTransportBuilderImpl)
}

@OptIn(RSocketTransportApi::class)
public sealed interface KtorTcpServerTransportBuilder : RSocketTransportBuilder<KtorTcpServerTransport> {
    public fun selectorManager(manager: SelectorManager, manage: Boolean)
    public fun socketOptions(block: SocketOptions.AcceptorOptions.() -> Unit)
}

private class KtorTcpServerTransportBuilderImpl : KtorTcpServerTransportBuilder {
    private var selectorManager: SelectorManager? = null
    private var manageSelectorManager: Boolean = true
    private var socketOptions: SocketOptions.AcceptorOptions.() -> Unit = {}

    override fun socketOptions(block: SocketOptions.AcceptorOptions.() -> Unit) {
        this.socketOptions = block
    }

    override fun selectorManager(manager: SelectorManager, manage: Boolean) {
        this.selectorManager = manager
        this.manageSelectorManager = manage
    }

    @RSocketTransportApi
    override fun buildTransport(context: CoroutineContext): KtorTcpServerTransport = KtorTcpServerTransportImpl(
        coroutineContext = context.supervisorContext() + Dispatchers.Default,
        socketOptions = socketOptions,
        selectorManager = selectorManager ?: SelectorManager(Dispatchers.IoCompatible),
        manageSelectorManager = manageSelectorManager
    )
}

private class KtorTcpServerTransportImpl(
    override val coroutineContext: CoroutineContext,
    private val socketOptions: SocketOptions.AcceptorOptions.() -> Unit,
    private val selectorManager: SelectorManager,
    manageSelectorManager: Boolean,
) : KtorTcpServerTransport {
    init {
        if (manageSelectorManager) coroutineContext.job.invokeOnCompletion { selectorManager.close() }
    }

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
    override suspend fun startServer(onConnection: (RSocketConnection) -> Unit): KtorTcpServerInstance {
        currentCoroutineContext().ensureActive()
        coroutineContext.ensureActive()

        return withContext(Dispatchers.IoCompatible) {
            val serverSocket = aSocket(selectorManager).tcp().bind(localAddress, socketOptions)
            KtorTcpServerInstanceImpl(
                coroutineContext = this@KtorTcpServerTargetImpl.coroutineContext.childContext(),
                serverSocket = serverSocket,
                onConnection = onConnection
            )
        }
    }
}

@RSocketTransportApi
private class KtorTcpServerInstanceImpl(
    override val coroutineContext: CoroutineContext,
    private val serverSocket: ServerSocket,
    private val onConnection: (RSocketConnection) -> Unit,
) : KtorTcpServerInstance {
    override val localAddress: SocketAddress get() = serverSocket.localAddress

    init {
        @OptIn(DelicateCoroutinesApi::class)
        launch(start = CoroutineStart.ATOMIC) {
            try {
                currentCoroutineContext().ensureActive() // because of ATOMIC start

                val connectionsContext = currentCoroutineContext().supervisorContext()
                while (true) {
                    val socket = serverSocket.accept()
                    onConnection(
                        KtorTcpConnection(
                            parentContext = connectionsContext,
                            socket = socket
                        )
                    )
                }
            } finally {
                nonCancellable {
                    serverSocket.close()
                    serverSocket.socketContext.join()
                }
            }
        }
    }
}
