package io.rsocket.kotlin.configuration

public sealed interface ReconnectConfiguration : ConnectConfiguration {
    //if connection failed, do we need reconnect?
    // depending on cause, f.e. if Setup error because of unsupported setup
    public fun reconnectOn(predicate: suspend (cause: Throwable?) -> Boolean)

    //when connection establishment failed, f.e. due to network issue
    // if specified, reconnectOn will be implicitly all time equals true
    public fun retryWhen(predicate: suspend (cause: Throwable, attempt: Long) -> Boolean)
}

internal class ReconnectConfigurationImpl(
    private val configurationState: ConfigurationState,
) : ReconnectConfiguration {
    var reconnectOn: (suspend (cause: Throwable?) -> Boolean)? = null
        private set

    var retryWhen: (suspend (cause: Throwable, attempt: Long) -> Boolean)? = null
        private set

    override fun reconnectOn(predicate: suspend (cause: Throwable?) -> Boolean) {
        configurationState.checkNotConfigured()
        reconnectOn = predicate
    }

    override fun retryWhen(predicate: suspend (cause: Throwable, attempt: Long) -> Boolean) {
        configurationState.checkNotConfigured()
        retryWhen = predicate
    }
}
