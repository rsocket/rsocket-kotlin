package io.rsocket.kotlin.configuration

@DslMarker
public annotation class ConnectConfigurationDsl

//marker interface
@ConnectConfigurationDsl
public sealed interface ConnectConfiguration
