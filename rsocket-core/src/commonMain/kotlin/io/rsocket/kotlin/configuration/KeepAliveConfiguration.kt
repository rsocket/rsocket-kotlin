package io.rsocket.kotlin.configuration

import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

public sealed interface KeepAliveConfiguration {
    public val interval: Duration
    public val maxLifetime: Duration
}

public sealed interface KeepAliveConnectConfiguration : KeepAliveConfiguration, ConnectConfiguration

public sealed interface KeepAliveClientConnectConfiguration : KeepAliveConnectConfiguration {
    public fun interval(duration: Duration)
    public fun maxLifetime(duration: Duration)
}

public sealed interface KeepAliveServerConnectConfiguration : KeepAliveConnectConfiguration

internal class KeepAliveClientConnectConfigurationImpl(
    private val configurationState: ConfigurationState,
) : KeepAliveClientConnectConfiguration {
    override var interval: Duration = 20.seconds
        private set
    override var maxLifetime: Duration = 90.seconds
        private set

    override fun interval(duration: Duration) {
        configurationState.checkNotConfigured()
        interval = duration
    }

    override fun maxLifetime(duration: Duration) {
        configurationState.checkNotConfigured()
        maxLifetime = duration
    }
}

internal class KeepAliveServerConnectConfigurationImpl(
    override val interval: Duration,
    override val maxLifetime: Duration,
) : KeepAliveServerConnectConfiguration
