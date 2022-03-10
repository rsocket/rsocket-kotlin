package io.rsocket.kotlin.configuration

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.payload.*
import kotlin.time.*

public sealed interface RSocketConfiguration {
    public val setup: SetupConfiguration
    public val payload: PayloadConfiguration
    public val keepAlive: KeepAliveConfiguration
}

public sealed interface RSocketConnectConfiguration : RSocketConfiguration, ConnectConfiguration {
    override val setup: SetupConnectConfiguration
    override val payload: PayloadConnectConfiguration
    override val keepAlive: KeepAliveConnectConfiguration
}

public sealed interface RSocketClientConnectConfiguration : RSocketConnectConfiguration {
    override val setup: SetupClientConnectConfiguration
    override val payload: PayloadClientConnectConfiguration
    override val keepAlive: KeepAliveClientConnectConfiguration

    public val reconnect: ReconnectConfiguration
}

public sealed interface RSocketServerConnectConfiguration : RSocketConnectConfiguration {
    override val setup: SetupServerConnectConfiguration
    override val payload: PayloadServerConnectConfiguration
    override val keepAlive: KeepAliveServerConnectConfiguration
}

internal abstract class RSocketConnectConfigurationImpl : RSocketConnectConfiguration, Closeable {
    abstract override val setup: SetupConnectConfigurationImpl

    override fun close() {
        setup.close()
    }
}

internal class RSocketClientConnectConfigurationImpl(
    configurationState: ConfigurationState,
) : RSocketClientConnectConfiguration, RSocketConnectConfigurationImpl() {
    override val setup: SetupClientConnectConfigurationImpl = SetupClientConnectConfigurationImpl(configurationState)
    override val payload: PayloadClientConnectConfigurationImpl = PayloadClientConnectConfigurationImpl(configurationState)
    override val keepAlive: KeepAliveClientConnectConfigurationImpl = KeepAliveClientConnectConfigurationImpl(configurationState)
    override val reconnect: ReconnectConfigurationImpl = ReconnectConfigurationImpl(configurationState)
}

internal class RSocketServerConnectConfigurationImpl(
    configurationState: ConfigurationState,
    keepAliveInterval: Duration,
    keepAliveMaxLifetime: Duration,
    metadataMimeType: MimeTypeWithName,
    dataMimeType: MimeTypeWithName,
    setupPayload: Payload,
) : RSocketServerConnectConfiguration, RSocketConnectConfigurationImpl() {
    override val setup: SetupServerConnectConfigurationImpl =
        SetupServerConnectConfigurationImpl(setupPayload)
    override val payload: PayloadServerConnectConfigurationImpl =
        PayloadServerConnectConfigurationImpl(configurationState, metadataMimeType, dataMimeType)
    override val keepAlive: KeepAliveServerConnectConfigurationImpl =
        KeepAliveServerConnectConfigurationImpl(keepAliveInterval, keepAliveMaxLifetime)
}
