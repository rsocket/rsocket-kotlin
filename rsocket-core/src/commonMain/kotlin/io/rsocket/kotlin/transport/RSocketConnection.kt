/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.transport

import kotlinx.coroutines.*
import kotlinx.io.*

// all methods can be called from any thread/context at any time
// should be accessed only internally
// should be implemented only by transports
@RSocketTransportApi
public sealed interface RSocketConnection : CoroutineScope

@RSocketTransportApi
public interface RSocketSequentialConnection : RSocketConnection {
    // throws if frame not sent
    // streamId=0 should be sent earlier
    public suspend fun sendFrame(streamId: Int, frame: Buffer)

    // null if no more frames could be received
    public suspend fun receiveFrame(): Buffer?
}

@RSocketTransportApi
public interface RSocketMultiplexedConnection : RSocketConnection {
    public suspend fun createStream(): Stream
    public suspend fun acceptStream(): Stream?

    @RSocketTransportApi
    public interface Stream : CoroutineScope {
        // 0 - highest priority
        // Int.MAX - lowest priority
        public fun setSendPriority(priority: Int)

        // throws if frame not sent
        public suspend fun sendFrame(frame: Buffer)

        // null if no more frames could be received
        public suspend fun receiveFrame(): Buffer?
    }
}
