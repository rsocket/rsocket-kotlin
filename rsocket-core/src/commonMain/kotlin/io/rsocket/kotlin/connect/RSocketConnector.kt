/*
 * Copyright 2015-2020 the original author or authors.
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

@file:OptIn(TransportApi::class, ExperimentalLoggingApi::class)

package io.rsocket.kotlin.connect

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.*

public sealed interface RSocketConnectorBuilder : RSocketPeerProviderBuilder {
    public fun beforeConfiguration(configurator: RSocketClientConnectConfigurator)
    public fun beforeConfiguration(vararg configurators: RSocketClientConnectConfigurator)

    public fun afterConfiguration(configurator: RSocketClientConnectConfigurator)
    public fun afterConfiguration(vararg configurators: RSocketClientConnectConfigurator)

    public fun defaultConfiguration(configurator: RSocketClientConnectConfigurator?)
}

public sealed interface RSocketConnector : RSocketPeerProvider {
    public suspend fun connect(
        transport: ClientTransport,
        configurator: RSocketClientConnectConfigurator? = null,
    ): ConnectedRSocket
}

public fun RSocketConnector(block: RSocketConnectorBuilder.() -> Unit = {}): RSocketConnector {
    val builder = RSocketConnectorBuilderImpl()
    builder.block()
    return builder.build()
}

private class RSocketConnectorBuilderImpl : RSocketConnectorBuilder, RSocketPeerBuilderImpl() {
    override fun beforeConfiguration(configurator: RSocketClientConnectConfigurator) {
        beforeConfigurators += configurator
    }

    override fun beforeConfiguration(vararg configurators: RSocketClientConnectConfigurator) {
        beforeConfigurators += configurators
    }

    override fun afterConfiguration(configurator: RSocketClientConnectConfigurator) {
        afterConfigurators += configurator
    }

    override fun afterConfiguration(vararg configurators: RSocketClientConnectConfigurator) {
        afterConfigurators += configurators
    }

    override fun defaultConfiguration(configurator: RSocketClientConnectConfigurator?) {
        defaultConfigurator = configurator
    }

    fun build(): RSocketConnector = RSocketConnectorImpl(
        loggerFactory = loggerFactory,
        beforeConfigurators = beforeConfigurators.toList(),
        afterConfigurators = afterConfigurators.toList(),
        defaultConfigurator = defaultConfigurator
    )
}

private class RSocketConnectorImpl(
    override val loggerFactory: LoggerFactory,
    private val beforeConfigurators: List<RSocketConnectConfigurator>,
    private val afterConfigurators: List<RSocketConnectConfigurator>,
    private val defaultConfigurator: RSocketConnectConfigurator?,
) : RSocketConnector {
    private val frameLogger = loggerFactory.logger("io.rsocket.kotlin.frame")
    private val connectionLogger = loggerFactory.logger("io.rsocket.kotlin.connection")

    override suspend fun connect(
        transport: ClientTransport,
        configurator: RSocketClientConnectConfigurator?,
    ): ConnectedRSocket {
        currentCoroutineContext().ensureActive()
        transport.ensureActive()

        val peerConfigurator = requireNotNull(configurator ?: defaultConfigurator) {
            "Configurator should be provided or on start or via default"
        }

        val peerContext = transport.coroutineContext + Job(transport.coroutineContext[Job])
        val session = RSocketSessionImpl(peerContext)
        val deferred = CompletableDeferred<RSocket>(peerContext.job)
        val connectContext = RSocketClientConnectContextImpl(session, deferred)
        val requester = withContext(peerContext) {
            connectContext.configure(beforeConfigurators, afterConfigurators, peerConfigurator)
            when {
                connectContext.configuration.reconnect.retryWhen != null -> connectWithReconnect(transport, connectContext)
                else                                                     -> connectOnce(transport, connectContext)
            }
        }
        deferred.complete(requester)
        return ConnectedRSocketImpl(connectContext.session, connectContext.configuration, requester)
    }

    private suspend fun connectOnce(transport: ClientTransport, connectContext: RSocketClientConnectContextImpl): RSocket {
        val connection = try {
            transport.connect().logging(frameLogger)
        } catch (cause: Throwable) {
            when (cause) {
                is CancellationException -> connectContext.session.cancel(cause)
                else                     -> connectContext.session.cancel("Connection creation failed", cause)
            }
            throw cause
        }
        connectContext.session.coroutineContext.job.invokeOnCompletion {
            when (it) {
                is CancellationException -> connection.cancel(it)
                else                     -> connection.cancel("Session closed", it)
            }
        }
        connection.coroutineContext.job.invokeOnCompletion {
            when (it) {
                is CancellationException -> connectContext.session.cancel(it)
                else                     -> connectContext.session.cancel("Connection closed", it)
            }
        }
        return connectContext.connect(connection)
    }

    private suspend fun connectWithReconnect(transport: ClientTransport, connectContext: RSocketClientConnectContextImpl): RSocket {
        val predicate = connectContext.configuration.reconnect.retryWhen!!
        val reconnectOn = connectContext.configuration.reconnect.reconnectOn
        val state = flow {
            emit(ReconnectState.Connecting) //init - state = connecting
            val connection = transport.connect().logging(frameLogger)
            val requester = connectContext.connect(connection)
            emit(ReconnectState.Connected(connection, requester)) //if connection established - state = connected
        }.retryWhen { cause, attempt -> //reconnection logic
            connectionLogger.debug(cause) { "Connection establishment failed, attempt: $attempt. Trying to reconnect..." }
            predicate(cause, attempt)
        }.catch { //reconnection failed - state = failed
            connectionLogger.debug(it) { "Reconnection failed" }
            emit(ReconnectState.Failed(it))
        }.transform { value ->
            emit(value) //emit before any action, to pass value directly to state

            when (value) {
                is ReconnectState.Connected -> {
                    connectionLogger.debug { "Connection established" }
                    val connectionJob = value.connection.coroutineContext.job
                    connectionJob.join() //await for connection completion
                    when (reconnectOn) {
                        null -> {}
                        else -> {
                            @OptIn(InternalCoroutinesApi::class)
                            val error = connectionJob.getCancellationException()
                            if (!reconnectOn(error.cause)) connectContext.session.cancel(error)
                        }
                    }
                    connectionLogger.debug { "Connection closed. Reconnecting..." }
                }
                is ReconnectState.Failed    -> connectContext.session.cancel("Reconnect failed", value.error) //reconnect failed, fail job
                ReconnectState.Connecting   -> Unit //skip, still waiting for new connection
            }
        }.restarting() //reconnect if old connection completed
            .stateIn(connectContext.session, SharingStarted.Eagerly, ReconnectState.Connecting)

        return ReconnectableRequester(state).apply {
            //await first connection to fail fast if something
            try {
                get()
            } catch (error: Throwable) {
                connectContext.session.cancel() //if during connecting, cancelled from user side
                throw error
            }
        }
    }
}

private fun Flow<ReconnectState>.restarting(): Flow<ReconnectState> = flow { while (true) emitAll(this@restarting) }
