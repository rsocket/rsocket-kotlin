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

package io.rsocket.kotlin.transport

import kotlinx.coroutines.*
import kotlin.coroutines.*

@SubclassOptInRequired(RSocketTransportApi::class)
public abstract class RSocketTransportFactory<Transport : RSocketTransport, Builder : RSocketTransportBuilder<Transport>>(
    @PublishedApi internal val createBuilder: () -> Builder,
) {
    @OptIn(RSocketTransportApi::class)
    public inline operator fun invoke(
        context: CoroutineContext,
        configure: Builder.() -> Unit = {},
    ): Transport = createBuilder().apply(configure).buildTransport(context)
}

@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketTransportBuilder<Transport : RSocketTransport> {
    @RSocketTransportApi
    public fun buildTransport(context: CoroutineContext): Transport
}

@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketTransport : CoroutineScope {
    // transports should have methods like:
    // `fun target(address: SocketAddress): RSocketClientTarget`
}

@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketClientTarget : CoroutineScope {
    // cancelling Job will cancel connection
    // Job will be completed when the connection is finished
    @RSocketTransportApi
    public fun connectClient(handler: RSocketConnectionHandler): Job
}

@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketServerTarget<Instance : RSocketServerInstance> : CoroutineScope {
    // handler will be called for all new connections
    @RSocketTransportApi
    public suspend fun startServer(handler: RSocketConnectionHandler): Instance
}

// cancelling it will cancel server
@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketServerInstance : CoroutineScope {
    // graceful closing API should be here
}
