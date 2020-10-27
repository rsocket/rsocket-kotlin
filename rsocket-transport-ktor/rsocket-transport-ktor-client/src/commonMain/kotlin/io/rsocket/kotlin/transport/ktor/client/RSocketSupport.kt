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

package io.rsocket.kotlin.transport.ktor.client

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.util.*
import io.rsocket.kotlin.core.*

public class RSocketSupport(
    internal val connector: RSocketConnector,
) {

    public class Config internal constructor() {
        public var connector: RSocketConnector = RSocketConnector()
    }

    public companion object Feature : HttpClientFeature<Config, RSocketSupport> {
        override val key: AttributeKey<RSocketSupport> = AttributeKey("RSocket")
        override fun prepare(block: Config.() -> Unit): RSocketSupport {
            val connector = Config().apply(block).connector
            return RSocketSupport(connector)
        }

        @OptIn(KtorExperimentalAPI::class)
        override fun install(feature: RSocketSupport, scope: HttpClient) {
            scope.feature(WebSockets) ?: error("RSocket require WebSockets to work. You must install WebSockets feature first.")
        }
    }
}
