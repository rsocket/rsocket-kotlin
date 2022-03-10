package io.rsocket.kotlin.configuration

import io.rsocket.kotlin.*

public sealed interface MimeTypeConfiguration {
    public val metadata: MimeType.WithName
    public val data: MimeType.WithName
}

public sealed interface MimeTypeConnectConfiguration : MimeTypeConfiguration, ConnectConfiguration

public sealed interface MimeTypeClientConnectConfiguration : MimeTypeConnectConfiguration {
    public fun metadata(mimeType: MimeType.WithName)
    public fun data(mimeType: MimeType.WithName)
}

public sealed interface MimeTypeServerConnectConfiguration : MimeTypeConnectConfiguration

internal class MimeTypeClientConnectConfigurationImpl(
    private val configurationState: ConfigurationState,
) : MimeTypeClientConnectConfiguration {
    override var metadata: MimeType.WithName = MimeType.WellKnown.ApplicationOctetStream
        private set
    override var data: MimeType.WithName = MimeType.WellKnown.ApplicationOctetStream
        private set

    override fun metadata(mimeType: MimeType.WithName) {
        configurationState.checkNotConfigured()
        metadata = mimeType
    }

    override fun data(mimeType: MimeType.WithName) {
        configurationState.checkNotConfigured()
        data = mimeType
    }
}

internal class MimeTypeServerConnectConfigurationImpl(
    override val metadata: MimeType.WithName,
    override val data: MimeType.WithName,
) : MimeTypeServerConnectConfiguration
