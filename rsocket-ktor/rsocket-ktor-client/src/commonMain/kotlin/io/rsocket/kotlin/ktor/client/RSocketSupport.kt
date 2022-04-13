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

package io.rsocket.kotlin.ktor.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.core.*

public class RSocketSupport private constructor(
    internal val connector: RSocketConnector,
    internal val bufferPool: ObjectPool<ChunkBuffer>
) {

    public class Config internal constructor() {
        public var bufferPool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
        public var connector: RSocketConnector = RSocketConnector()
        public fun connector(block: RSocketConnectorBuilder.() -> Unit) {
            connector = RSocketConnector(block)
        }
    }

    public companion object Plugin : HttpClientPlugin<Config, RSocketSupport> {
        override val key: AttributeKey<RSocketSupport> = AttributeKey("RSocket")
        override fun prepare(block: Config.() -> Unit): RSocketSupport = Config().run {
            block()
            RSocketSupport(connector, bufferPool)
        }

        override fun install(plugin: RSocketSupport, scope: HttpClient) {
            scope.pluginOrNull(WebSockets)
                ?: error("RSocket require WebSockets to work. You must install WebSockets plugin first.")
        }
    }
}
