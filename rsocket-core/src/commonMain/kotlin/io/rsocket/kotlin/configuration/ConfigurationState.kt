package io.rsocket.kotlin.configuration

internal interface ConfigurationState {
    fun checkConfigured()
    fun checkNotConfigured()
}
