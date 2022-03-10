package io.rsocket.kotlin.configuration

import io.rsocket.kotlin.*

public sealed interface PayloadConfiguration {
    //TODO: Long?
    //TODO: add later
    //max payload overall size
//    public val maxSize: Long

    //TODO: rename
    //size of fragment, after fragmentation
    // f.e. if 100, and payload = 450, will create 5 fragments when sending
    public val maxFragmentSize: Int

    public val mimeType: MimeTypeConfiguration
}

public sealed interface PayloadConnectConfiguration : PayloadConfiguration, ConnectConfiguration {
    override val mimeType: MimeTypeConnectConfiguration

    //    public fun maxSize(value: Int)
    public fun maxFragmentSize(value: Int)
}

public sealed interface PayloadClientConnectConfiguration : PayloadConnectConfiguration {
    override val mimeType: MimeTypeClientConnectConfiguration
}

public sealed interface PayloadServerConnectConfiguration : PayloadConnectConfiguration {
    override val mimeType: MimeTypeServerConnectConfiguration
}

internal abstract class PayloadConnectConfigurationImpl(
    private val configurationState: ConfigurationState,
) : PayloadConnectConfiguration {
    final override var maxFragmentSize: Int = 0
        private set

    final override fun maxFragmentSize(value: Int) {
        configurationState.checkNotConfigured()
        require(value == 0 || value >= 64) {
            "maxFragmentSize should be zero (no fragmentation) or greater than or equal to 64, but was $value"
        }
        maxFragmentSize = value
    }
}

internal class PayloadClientConnectConfigurationImpl(
    configurationState: ConfigurationState,
) : PayloadClientConnectConfiguration, PayloadConnectConfigurationImpl(configurationState) {
    override val mimeType: MimeTypeClientConnectConfigurationImpl =
        MimeTypeClientConnectConfigurationImpl(configurationState)
}

internal class PayloadServerConnectConfigurationImpl(
    configurationState: ConfigurationState,
    metadataMimeType: MimeType.WithName,
    dataMimeType: MimeType.WithName,
) : PayloadServerConnectConfiguration, PayloadConnectConfigurationImpl(configurationState) {
    override val mimeType: MimeTypeServerConnectConfigurationImpl =
        MimeTypeServerConnectConfigurationImpl(metadataMimeType, dataMimeType)
}
