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

import kotlinx.coroutines.*
import kotlinx.io.*

@RSocketTransportApi
public sealed interface RSocketConnection<Context> : CoroutineScope

@RSocketTransportApi
public interface SequentialRSocketConnection<Context> : RSocketConnection<Context> {
    public val isClosedForSend: Boolean

    // streamId = 0, prioritized
    public suspend fun sendConnectionFrame(frame: Buffer)

    // streamId = X
    public suspend fun sendStreamFrame(frame: Buffer)

    public fun startReceiving(inbound: Inbound)

    @RSocketTransportApi
    public interface Inbound {
        public fun onFrame(frame: Buffer)
    }
}

@RSocketTransportApi
public interface MultiplexedRSocketConnection<Context> : RSocketConnection<Context> {
    // streamId = 0, initial stream
    public suspend fun sendConnectionFrame(frame: Buffer)

    // streamId = X
    public suspend fun createStream(): Stream

    public fun startReceiving(inbound: Inbound)

    @RSocketTransportApi
    public interface Inbound {
        // streamId = 0, initial stream
        public fun onConnectionFrame(frame: Buffer)

        // streamId = X
        public fun onStream(frame: Buffer, stream: Stream)
    }

    // closing stream will send buffered frames (if needed)
    // sending/receiving frames will be not possible after it
    @RSocketTransportApi
    public interface Stream : AutoCloseable {
        public val isClosedForSend: Boolean

        // throws if frame not sent
        public suspend fun sendFrame(frame: Buffer)

        public fun startReceiving(inbound: Inbound)

        @RSocketTransportApi
        public interface Inbound {
            public fun onFrame(frame: Buffer)
            public fun onClose()
        }
    }
}
