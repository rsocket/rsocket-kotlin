package io.rsocket.kotlin.connect

import io.rsocket.kotlin.*
import io.rsocket.kotlin.logging.*

public sealed interface RSocketPeerProvider {
    @RSocketLoggingApi
    public val loggerFactory: LoggerFactory
}

//TODO: naming
public sealed interface RSocketPeerProviderBuilder {
    @RSocketLoggingApi
    public fun loggerFactory(factory: LoggerFactory)

    public fun beforePeerConfiguration(configurator: RSocketConnectConfigurator)
    public fun beforePeerConfiguration(vararg configurators: RSocketConnectConfigurator)

    public fun afterPeerConfiguration(configurator: RSocketConnectConfigurator)
    public fun afterPeerConfiguration(vararg configurators: RSocketConnectConfigurator)

    public fun defaultPeerConfiguration(configurator: RSocketConnectConfigurator?)
}

internal abstract class RSocketPeerBuilderImpl : RSocketPeerProviderBuilder {
    @RSocketLoggingApi
    protected var loggerFactory: LoggerFactory = DefaultLoggerFactory
        private set

    protected val beforeConfigurators = mutableListOf<RSocketConnectConfigurator>()
    protected val afterConfigurators = mutableListOf<RSocketConnectConfigurator>()
    protected var defaultConfigurator: RSocketConnectConfigurator? = null

    @RSocketLoggingApi
    final override fun loggerFactory(factory: LoggerFactory) {
        loggerFactory = factory
    }

    final override fun beforePeerConfiguration(configurator: RSocketConnectConfigurator) {
        beforeConfigurators += configurator
    }

    final override fun beforePeerConfiguration(vararg configurators: RSocketConnectConfigurator) {
        beforeConfigurators += configurators
    }

    final override fun afterPeerConfiguration(configurator: RSocketConnectConfigurator) {
        afterConfigurators += configurator
    }

    final override fun afterPeerConfiguration(vararg configurators: RSocketConnectConfigurator) {
        afterConfigurators += configurators
    }

    override fun defaultPeerConfiguration(configurator: RSocketConnectConfigurator?) {
        defaultConfigurator = configurator
    }
}
