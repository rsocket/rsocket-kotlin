package io.rsocket.kotlin.connect

public fun interface RSocketConnectConfigurator {
    public suspend fun RSocketConnectContext.configure()
}

public fun interface RSocketClientConnectConfigurator : RSocketConnectConfigurator {
    override suspend fun RSocketConnectContext.configure() {
        if (this is RSocketClientConnectContext) configure()
    }

    public suspend fun RSocketClientConnectContext.configure()
}

public fun interface RSocketServerConnectConfigurator : RSocketConnectConfigurator {
    override suspend fun RSocketConnectContext.configure() {
        if (this is RSocketServerConnectContext) configure()
    }

    public suspend fun RSocketServerConnectContext.configure()
}
