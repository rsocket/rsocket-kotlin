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

package io.rsocket.kotlin.transport

import kotlinx.io.*
import kotlin.coroutines.*

public interface RSocketConnectionContext

public fun interface RSocketConnectionContextTransformer<OldContext : RSocketConnectionContext, NewContext : RSocketConnectionContext> {
    public fun transformContext(context: OldContext): NewContext
}

// TODO: better name for function
@OptIn(RSocketTransportApi::class)
public fun <
        OldContext : RSocketConnectionContext,
        NewContext : RSocketConnectionContext,
        > RSocketClientTarget<OldContext>.withMappedContext(
    transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
): RSocketClientTarget<NewContext> = MappedRSocketClientTarget(this, transformer)

@OptIn(RSocketTransportApi::class)
public fun <
        OldContext : RSocketConnectionContext,
        NewContext : RSocketConnectionContext,
        ServerConfiguration,
        > RSocketServerTarget<OldContext, ServerConfiguration>.withMappedContext(
    transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
): RSocketServerTarget<NewContext, ServerConfiguration> = MappedRSocketServerTarget(this, transformer)

@RSocketTransportApi
private class MappedRSocketClientTarget<OldContext : RSocketConnectionContext, NewContext : RSocketConnectionContext>(
    private val delegate: RSocketClientTarget<OldContext>,
    private val transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
) : RSocketClientTarget<NewContext> {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    @RSocketTransportApi
    override suspend fun <T> connectClient(initializer: RSocketConnectionInitializer<NewContext, T>): T {
        return delegate.connectClient(MappedRSocketConnectionInitializer(initializer, transformer))
    }
}

@RSocketTransportApi
private class MappedRSocketServerTarget<OldContext : RSocketConnectionContext, NewContext : RSocketConnectionContext, ServerConfiguration>(
    private val delegate: RSocketServerTarget<OldContext, ServerConfiguration>,
    private val transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
) : RSocketServerTarget<NewContext, ServerConfiguration> {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    @RSocketTransportApi
    override suspend fun startServer(initializer: RSocketConnectionInitializer<NewContext, Unit>): RSocketServerInstance<ServerConfiguration> {
        return delegate.startServer(MappedRSocketConnectionInitializer(initializer, transformer))
    }
}

@RSocketTransportApi
private class MappedRSocketConnectionInitializer<OldContext : RSocketConnectionContext, NewContext : RSocketConnectionContext, T>(
    private val delegate: RSocketConnectionInitializer<NewContext, T>,
    private val transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
) : RSocketConnectionInitializer<OldContext, T> {
    override suspend fun RSocketConnection<OldContext>.initializeConnection(): T = with(delegate) {
        when (val connection = this@initializeConnection) {
            is RSocketMultiplexedConnection<OldContext> -> MappedRSocketMultiplexedConnection(connection, transformer)
            is RSocketSequentialConnection<OldContext>  -> MappedRSocketSequentialConnection(connection, transformer)
        }.initializeConnection()
    }
}

@RSocketTransportApi
private class MappedRSocketSequentialConnection<OldContext : RSocketConnectionContext, NewContext : RSocketConnectionContext>(
    private val delegate: RSocketSequentialConnection<OldContext>,
    transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
) : RSocketSequentialConnection<NewContext> {
    override val connectionContext: NewContext = transformer.transformContext(delegate.connectionContext)
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext
    override val isClosedForSend: Boolean get() = delegate.isClosedForSend
    override suspend fun sendFrame(streamId: Int, frame: Buffer): Unit = delegate.sendFrame(streamId, frame)
    override suspend fun receiveFrame(): Buffer? = delegate.receiveFrame()
}

@RSocketTransportApi
private class MappedRSocketMultiplexedConnection<OldContext : RSocketConnectionContext, NewContext : RSocketConnectionContext>(
    private val delegate: RSocketMultiplexedConnection<OldContext>,
    transformer: RSocketConnectionContextTransformer<OldContext, NewContext>,
) : RSocketMultiplexedConnection<NewContext> {
    override val connectionContext: NewContext = transformer.transformContext(delegate.connectionContext)
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext
    override suspend fun createStream(): RSocketMultiplexedConnection.Stream = delegate.createStream()
    override suspend fun acceptStream(): RSocketMultiplexedConnection.Stream? = delegate.acceptStream()
}
