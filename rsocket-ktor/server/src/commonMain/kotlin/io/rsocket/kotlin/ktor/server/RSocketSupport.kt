/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.ktor.server

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.rsocket.kotlin.core.*

public class RSocketSupport private constructor(
    internal val server: RSocketServer,
) {
    public class Config internal constructor() {
        public var server: RSocketServer = RSocketServer()
        public fun server(block: RSocketServerBuilder.() -> Unit) {
            server = RSocketServer(block)
        }
    }

    public companion object Feature : BaseApplicationPlugin<Application, Config, RSocketSupport> {
        override val key: AttributeKey<RSocketSupport> = AttributeKey("RSocket")
        override fun install(pipeline: Application, configure: Config.() -> Unit): RSocketSupport {
            pipeline.pluginOrNull(WebSockets)
                ?: error("RSocket require WebSockets to work. You must install WebSockets plugin first.")

            return Config().run {
                configure()
                RSocketSupport(server)
            }
        }
    }
}
