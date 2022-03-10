package io.rsocket.kotlin.configuration

import io.rsocket.kotlin.core.*

public sealed interface MimeTypeConfiguration {
    public val metadata: MimeTypeWithName
    public val data: MimeTypeWithName
}

public sealed interface MimeTypeConnectConfiguration : MimeTypeConfiguration, ConnectConfiguration

public sealed interface MimeTypeClientConnectConfiguration : MimeTypeConnectConfiguration {
    public fun metadata(mimeType: MimeTypeWithName)
    public fun data(mimeType: MimeTypeWithName)
}

public sealed interface MimeTypeServerConnectConfiguration : MimeTypeConnectConfiguration

internal class MimeTypeClientConnectConfigurationImpl(
    private val configurationState: ConfigurationState,
) : MimeTypeClientConnectConfiguration {
    override var metadata: MimeTypeWithName = WellKnownMimeType.ApplicationOctetStream
        private set
    override var data: MimeTypeWithName = WellKnownMimeType.ApplicationOctetStream
        private set

    override fun metadata(mimeType: MimeTypeWithName) {
        configurationState.checkNotConfigured()
        metadata = mimeType
    }

    override fun data(mimeType: MimeTypeWithName) {
        configurationState.checkNotConfigured()
        data = mimeType
    }
}

internal class MimeTypeServerConnectConfigurationImpl(
    override val metadata: MimeTypeWithName,
    override val data: MimeTypeWithName,
) : MimeTypeServerConnectConfiguration
