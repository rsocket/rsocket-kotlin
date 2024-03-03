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

public sealed interface KtorTcpClientTarget : RSocketClientTarget {
    public val remoteAddress: InetSocketAddress
}

public sealed interface KtorTcpClientTransport : RSocketTransport<
        InetSocketAddress,
        KtorTcpClientTarget> {

    public fun target(hostname: String, port: Int): KtorTcpClientTarget = target(InetSocketAddress(hostname, port))

    public companion object Factory :
        RSocketTransportFactory<
                InetSocketAddress,
                KtorTcpClientTarget,
                KtorTcpClientTransport,
                KtorTcpClientTransportBuilder>(::KtorTcpClientTransportBuilderImpl)
}

public sealed interface KtorTcpClientTransportBuilder : RSocketTransportBuilder<
        InetSocketAddress,
        KtorTcpClientTarget,
        KtorTcpClientTransport> {

    public fun dispatcher(context: CoroutineContext)
    public fun inheritDispatcher(): Unit = dispatcher(EmptyCoroutineContext)
    public fun selectorManagerDispatcher(context: CoroutineContext)
    public fun selectorManager(manager: SelectorManager, manage: Boolean)

    public fun socketOptions(block: SocketOptions.TCPClientSocketOptions.() -> Unit)

    //TODO: TLS support
}

private class KtorTcpClientTransportBuilderImpl : KtorTcpClientTransportBuilder {
    private var dispatcher: CoroutineContext = Dispatchers.IO
    private var selector: KtorTcpSelector? = null
    private var socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit = {}

    override fun dispatcher(context: CoroutineContext) {
        check(context[Job] == null) { "Dispatcher shouldn't contain job" }
        this.dispatcher = context
    }

    override fun socketOptions(block: SocketOptions.TCPClientSocketOptions.() -> Unit) {
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
    override fun buildTransport(context: CoroutineContext): KtorTcpClientTransport = KtorTcpClientTransportImpl(
        coroutineContext = context.supervisorContext() + dispatcher,
        socketOptions = socketOptions,
        selector = selector
    )
}

private class KtorTcpClientTransportImpl private constructor(
    override val coroutineContext: CoroutineContext,
    private val socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit,
    private val selectorManager: SelectorManager,
) : KtorTcpClientTransport {
    constructor(
        coroutineContext: CoroutineContext,
        socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit,
        selector: KtorTcpSelector?,
    ) : this(coroutineContext, socketOptions, selector.createFor(coroutineContext))

    override fun target(address: InetSocketAddress): KtorTcpClientTarget = KtorTcpClientTargetImpl(
        coroutineContext = coroutineContext.supervisorContext(),
        remoteAddress = address,
        socketOptions = socketOptions,
        selectorManager = selectorManager
    )
}

private class KtorTcpClientTargetImpl(
    override val coroutineContext: CoroutineContext,
    override val remoteAddress: InetSocketAddress,
    private val socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit,
    private val selectorManager: SelectorManager,
) : KtorTcpClientTarget {

    @RSocketTransportApi
    override suspend fun createSession(): RSocketTransportSession {
        return launchCont {
            val socket = aSocket(selectorManager).tcp().connect(remoteAddress, socketOptions)
            KtorTcpSession(
                coroutineContext = coroutineContext.childContext(),
                socket = socket
            )
        }
    }
}
