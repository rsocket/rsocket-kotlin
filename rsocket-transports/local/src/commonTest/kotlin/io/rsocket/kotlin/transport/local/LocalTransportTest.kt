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

package io.rsocket.kotlin.transport.local

import io.rsocket.kotlin.transport.tests.*
import kotlinx.coroutines.channels.*

class OldLocalTransportTest : TransportTest() {
    override suspend fun before() {
        val server = startServer(LocalServerTransport())
        client = connectClient(server)
    }
}

abstract class LocalTransportTest(
    private val configure: LocalServerTransportBuilder.() -> Unit,
) : TransportTest() {
    override suspend fun before() {
        val server = startServer(LocalServerTransport(testContext, configure).target())
        client = connectClient(LocalClientTransport(testContext).target(server.serverName))
    }
}

class SequentialBufferedLocalTransportTest : LocalTransportTest({
    sequential(prioritizationQueueBuffersCapacity = Channel.BUFFERED)
})

class SequentialUnlimitedLocalTransportTest : LocalTransportTest({
    sequential(prioritizationQueueBuffersCapacity = Channel.UNLIMITED)
})

class MultiplexedBufferedLocalTransportTest : LocalTransportTest({
    multiplexed(
        streamsQueueCapacity = Channel.BUFFERED,
        streamBufferCapacity = Channel.BUFFERED
    )
})

class MultiplexedUnlimitedLocalTransportTest : LocalTransportTest({
    multiplexed(
        streamsQueueCapacity = Channel.UNLIMITED,
        streamBufferCapacity = Channel.UNLIMITED
    )
})
