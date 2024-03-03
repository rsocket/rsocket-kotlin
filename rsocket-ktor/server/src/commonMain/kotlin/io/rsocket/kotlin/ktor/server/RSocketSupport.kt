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
import io.rsocket.kotlin.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.transport.*
import io.rsocket.kotlin.transport.ktor.websocket.internal.*
import kotlinx.coroutines.*

public class RSocketSupport private constructor(
    private val server: RSocketServer,
) {
    @RSocketTransportApi
    internal fun handler(acceptor: ConnectionAcceptor): suspend DefaultWebSocketServerSession.() -> Unit {
        val serverAcceptor = server.createAcceptor(acceptor)
        return {
            serverAcceptor.acceptSession(KtorWebSocketSession(this))
            coroutineContext.job.join()
        }
    }

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
