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

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

@RSocketTransportApi
public sealed interface RSocketTransportSession : CoroutineScope {
    public interface Sequential : RSocketTransportSession {
        public suspend fun sendFrame(frame: ByteReadPacket)
        public suspend fun receiveFrame(): ByteReadPacket
    }

    public interface Multiplexed : RSocketTransportSession {
        public interface Stream : CoroutineScope {
            // value should be positive
            // if underlying transport doesn't support it - no error should be thrown
            // if underlying transport needs value in range of 0 to 128 - scale on transport level? - TODO
            public fun updatePriority(value: Int)
            public suspend fun sendFrame(frame: ByteReadPacket)
            public suspend fun receiveFrame(): ByteReadPacket
        }

        public suspend fun createStream(): Stream
        public suspend fun awaitStream(): Stream
    }
}
