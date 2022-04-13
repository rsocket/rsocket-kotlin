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

package io.rsocket.kotlin.transport.ktor.websocket.server

import io.rsocket.kotlin.test.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

class CIOWebSocketTransportTest : WebSocketTransportTest(ClientCIO, ServerCIO) {
    //on native we need more time here
    override val testTimeout: Duration = 5.minutes

    //tests are ignored, because current CIO:native websockets implementation is unstable when working with large frames
    @Test
    @IgnoreNative
    override fun largePayloadFireAndForget10() = super.largePayloadFireAndForget10()

    @Test
    @IgnoreNative
    override fun largePayloadMetadataPush10() = super.largePayloadMetadataPush10()

    @Test
    @IgnoreNative
    override fun largePayloadRequestChannel200() = super.largePayloadRequestChannel200()

    @Test
    @IgnoreNative
    override fun largePayloadRequestResponse100() = super.largePayloadRequestResponse100()
}
