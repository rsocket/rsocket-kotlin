/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.core

import io.ktor.application.*
import io.ktor.util.*
import io.ktor.websocket.*
import io.rsocket.kotlin.flow.*
import io.rsocket.kotlin.plugin.*

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
