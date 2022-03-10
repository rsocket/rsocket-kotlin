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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.transport.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

public sealed interface RSocketServerBuilder : RSocketPeerProviderBuilder {
    //TODO: add later
//    public fun maxTimeToFirstFrame(timeout: Duration)

    public fun beforeConfiguration(configurator: RSocketServerConnectConfigurator)
    public fun beforeConfiguration(vararg configurators: RSocketServerConnectConfigurator)

    public fun afterConfiguration(configurator: RSocketServerConnectConfigurator)
    public fun afterConfiguration(vararg configurators: RSocketServerConnectConfigurator)

    public fun defaultConfiguration(configurator: RSocketServerConnectConfigurator?)
}

public sealed interface RSocketServer : RSocketPeerProvider {
    @DelicateCoroutinesApi
    public fun <T> bind(
        transport: ServerTransport<T>,
        configurator: RSocketServerConnectConfigurator? = null,
    ): T

    public fun <T> bindIn(
        scope: CoroutineScope,
        transport: ServerTransport<T>,
        configurator: RSocketServerConnectConfigurator? = null,
    ): T
}

public fun RSocketServer(block: RSocketServerBuilder.() -> Unit = {}): RSocketServer {
    val builder = RSocketServerBuilderImpl()
    builder.block()
    return builder.build()
}

private class RSocketServerBuilderImpl : RSocketServerBuilder, RSocketPeerBuilderImpl() {
    override fun beforeConfiguration(configurator: RSocketServerConnectConfigurator) {
        beforeConfigurators += configurator
    }

    override fun beforeConfiguration(vararg configurators: RSocketServerConnectConfigurator) {
        beforeConfigurators += configurators
    }

    override fun afterConfiguration(configurator: RSocketServerConnectConfigurator) {
        afterConfigurators += configurator
    }

    override fun afterConfiguration(vararg configurators: RSocketServerConnectConfigurator) {
        afterConfigurators += configurators
    }

    override fun defaultConfiguration(configurator: RSocketServerConnectConfigurator?) {
        defaultConfigurator = configurator
    }

    fun build(): RSocketServer = RSocketServerImpl(
        loggerFactory = loggerFactory,
        beforeConfigurators = beforeConfigurators.toList(),
        afterConfigurators = afterConfigurators.toList(),
        defaultConfigurator = defaultConfigurator
    )
}

private class RSocketServerImpl(
    override val loggerFactory: LoggerFactory,
    private val beforeConfigurators: List<RSocketConnectConfigurator>,
    private val afterConfigurators: List<RSocketConnectConfigurator>,
    private val defaultConfigurator: RSocketConnectConfigurator?,
) : RSocketServer {
    private val frameLogger = loggerFactory.logger("io.rsocket.kotlin.frame")

    @DelicateCoroutinesApi
    override fun <T> bind(
        transport: ServerTransport<T>,
        configurator: RSocketServerConnectConfigurator?,
    ): T = bindIn(GlobalScope, transport, configurator)

    override fun <T> bindIn(
        scope: CoroutineScope,
        transport: ServerTransport<T>,
        configurator: RSocketServerConnectConfigurator?,
    ): T {
        val peerConfigurator = requireNotNull(configurator ?: defaultConfigurator) {
            "Configurator should be provided or on start or via default"
        }

        return with(transport) {
            scope.start {
                val connection = it.logging(frameLogger)
                try {
                    connection.receiveFrame { setupFrame ->
                        when {
                            setupFrame !is SetupFrame             -> throw RSocketError.Setup.Invalid("Invalid setup frame: ${setupFrame.type}")
                            setupFrame.version != Version.Current -> throw RSocketError.Setup.Unsupported("Unsupported version: ${setupFrame.version}")
                            setupFrame.honorLease                 -> throw RSocketError.Setup.Unsupported("Lease is not supported")
                            setupFrame.resumeToken != null        -> throw RSocketError.Setup.Unsupported("Resume is not supported")
                            else                                  -> {
                                val peerContext = coroutineContext
                                val session = RSocketSessionImpl(peerContext)
                                val deferred = CompletableDeferred<RSocket>(peerContext.job)
                                val connectContext = RSocketServerConnectContextImpl(
                                    session,
                                    deferred,
                                    keepAliveInterval = setupFrame.keepAliveIntervalMillis.milliseconds,
                                    keepAliveMaxLifetime = setupFrame.keepAliveMaxLifetimeMillis.milliseconds,
                                    metadataMimeType = WellKnownMimeType(setupFrame.metadataMimeTypeText)
                                        ?: CustomMimeType(setupFrame.metadataMimeTypeText),
                                    dataMimeType = WellKnownMimeType(setupFrame.dataMimeTypeText)
                                        ?: CustomMimeType(setupFrame.dataMimeTypeText),
                                    setupPayload = setupFrame.payload,
                                )
                                connectContext.configure(beforeConfigurators, afterConfigurators, peerConfigurator)
                                deferred.complete(connectContext.connect(connection))
                                connection.coroutineContext.job.invokeOnCompletion {
                                    when (it) {
                                        is CancellationException -> connectContext.session.cancel(it)
                                        else                     -> connectContext.session.cancel("Connection closed", it)
                                    }
                                }
                            }
                        }
                    }
                } catch (cause: Throwable) {
                    connection.failSetup(cause)
                }
                awaitCancellation()
            }
        }
    }

    @Suppress("SuspendFunctionOnCoroutineScope")
    private suspend fun Connection.failSetup(cause: Throwable): Nothing {
        val error =
            cause as? RSocketError.Setup ?: RSocketError.Setup.Rejected(cause.message ?: "Rejected by server acceptor")
        sendFrame(ErrorFrame(0, error))
        cancel("Connection establishment failed", error)
        throw error
    }

}
