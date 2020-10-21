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

import io.rsocket.kotlin.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

public class RSocketConnectorBuilder internal constructor() {
    public var loggerFactory: LoggerFactory = DefaultLoggerFactory

    private val connectionConfig: ConnectionConfigBuilder = ConnectionConfigBuilder()
    private val interceptors: InterceptorsBuilder = InterceptorsBuilder()
    private var acceptor: ConnectionAcceptor? = null

    public fun connectionConfig(configure: ConnectionConfigBuilder.() -> Unit) {
        connectionConfig.configure()
    }

    public fun interceptors(configure: InterceptorsBuilder.() -> Unit) {
        interceptors.configure()
    }

    public fun acceptor(block: ConnectionAcceptor?) {
        acceptor = block
    }

    public class ConnectionConfigBuilder internal constructor() {
        public var keepAlive: KeepAlive = KeepAlive()
        public var payloadMimeType: PayloadMimeType = PayloadMimeType()
        private var setupPayload: (() -> Payload)? = null

        public fun setupPayload(block: (() -> Payload)?) {
            setupPayload = block
        }

        public fun setupPayload(payload: Payload): Unit = setupPayload { payload.copy() }

        internal fun producer(): () -> ConnectionConfig {
            val keepAlive = this.keepAlive
            val payloadMimeType = this.payloadMimeType
            val setupPayload = this.setupPayload
            return {
                ConnectionConfig(
                    keepAlive = keepAlive,
                    payloadMimeType = payloadMimeType,
                    setupPayload = setupPayload?.invoke() ?: Payload.Empty
                )
            }
        }
    }

    internal fun build(): RSocketConnector = RSocketConnector(
        loggerFactory,
        interceptors.build(),
        connectionConfig.producer(),
        acceptor ?: defaultAcceptor,
    )

    private companion object {
        private val defaultAcceptor: ConnectionAcceptor = ConnectionAcceptor { EmptyRSocket }

        private object EmptyRSocket : RSocket {
            override val job: Job = NonCancellable
        }
    }
}

public fun RSocketConnector(configure: RSocketConnectorBuilder.() -> Unit = {}): RSocketConnector {
    val builder = RSocketConnectorBuilder()
    builder.configure()
    return builder.build()
}
