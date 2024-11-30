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

package io.rsocket.kotlin.transport

import kotlinx.io.*

// all methods can be called from any thread/context at any time
// should be accessed only internally
// should be implemented only by transports
@RSocketTransportApi
public sealed interface RSocketConnection

@RSocketTransportApi
public fun interface RSocketConnectionHandler {
    public suspend fun handleConnection(connection: RSocketConnection)
}

@RSocketTransportApi
public interface RSocketSequentialConnection : RSocketConnection {
    // TODO: is it needed for connection?
    public val isClosedForSend: Boolean

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

    public interface Stream : AutoCloseable {
        public val isClosedForSend: Boolean

        // 0 - highest priority
        // Int.MAX - lowest priority
        public fun setSendPriority(priority: Int)

        // throws if frame not sent
        public suspend fun sendFrame(frame: Buffer)

        // null if no more frames could be received
        public suspend fun receiveFrame(): Buffer?

        // closing stream will send buffered frames (if needed)
        // sending/receiving frames will be not possible after it
        // should not throw
        override fun close()
    }
}
