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
import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.plugin.*

class RSocketConnector(
    private val connectionProvider: ConnectionProvider,
    private val configuration: RSocketConnectorConfiguration = RSocketConnectorConfiguration(),
) {

    suspend fun connect(): RSocket {
        val connection =
            connectionProvider.connect()
                .let(configuration.plugin::wrapConnection)
                .logging(configuration.loggerFactory.logger("io.rsocket.kotlin.frame.Frame"))

        val setupFrame = SetupFrame(
            version = Version.Current,
            honorLease = false,
            keepAlive = configuration.keepAlive,
            resumeToken = null,
            payloadMimeType = configuration.payloadMimeType,
            payload = configuration.setupPayload
        )
        return connectClient(
            connection = connection,
            plugin = configuration.plugin,
            setupFrame = setupFrame,
            ignoredFrameConsumer = configuration.ignoredFrameConsumer,
            acceptor = configuration.acceptor
        )
    }
}
