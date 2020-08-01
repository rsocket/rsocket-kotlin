package io.rsocket.server

import io.ktor.application.*
import io.ktor.util.*
import io.ktor.websocket.*
import io.rsocket.flow.*
import io.rsocket.plugin.*

class RSocketServerSupport(
    internal val configuration: RSocketServerConfiguration
) {
    class Config internal constructor() {
        var plugin: Plugin = Plugin()
        var fragmentation: Int = 0
        var requestStrategy: () -> RequestStrategy = RequestStrategy.Default

        internal fun build(): RSocketServerSupport = RSocketServerSupport(
            RSocketServerConfiguration(
                plugin = plugin,
                fragmentation = fragmentation,
                requestStrategy = requestStrategy
            )
        )
    }

    companion object Feature : ApplicationFeature<Application, Config, RSocketServerSupport> {
        override val key = AttributeKey<RSocketServerSupport>("RSocket")
        override fun install(pipeline: Application, configure: Config.() -> Unit): RSocketServerSupport {
            pipeline.install(WebSockets)
            return Config().apply(configure).build()
        }
    }
}
