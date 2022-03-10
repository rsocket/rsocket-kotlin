package io.rsocket.kotlin.configuration

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.payload.*

public sealed interface SetupConfiguration {
    //every call to payload will create a copy of payload set in configuration
    // so it's better to always call `use` on it
    public val payload: Payload
}

public sealed interface SetupConnectConfiguration : SetupConfiguration, ConnectConfiguration

public sealed interface SetupClientConnectConfiguration : SetupConnectConfiguration {
    public fun payload(payload: Payload)
}

public sealed interface SetupServerConnectConfiguration : SetupConnectConfiguration

internal abstract class SetupConnectConfigurationImpl : SetupConnectConfiguration, Closeable

internal class SetupClientConnectConfigurationImpl(
    private val configurationState: ConfigurationState,
) : SetupClientConnectConfiguration, SetupConnectConfigurationImpl() {
    private var _payload: Payload? = null
    override val payload: Payload get() = _payload?.copy() ?: Payload.Empty

    override fun payload(payload: Payload) {
        configurationState.checkNotConfigured()
        check(_payload == null) { "Payload can be set only once" }
        _payload = payload
    }

    override fun close() {
        _payload?.close()
    }
}

internal class SetupServerConnectConfigurationImpl(
    private var _payload: Payload,
) : SetupServerConnectConfiguration, SetupConnectConfigurationImpl() {
    override val payload: Payload get() = _payload.copy()
    override fun close() {
        _payload.close()
    }
}
