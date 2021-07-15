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

package io.rsocket.kotlin.internal

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.test.*

class PrioritizerTest : SuspendTest, TestWithLeakCheck {
    private val prioritizer = Prioritizer(Channel.UNLIMITED)

    @Test
    fun testOrdering() = test {
        prioritizer.send(CancelFrame(1))
        prioritizer.send(CancelFrame(2))
        prioritizer.send(CancelFrame(3))

        assertEquals(1, prioritizer.receive().streamId)
        assertEquals(2, prioritizer.receive().streamId)
        assertEquals(3, prioritizer.receive().streamId)
    }

    @Test
    fun testOrderingPriority() = test {
        prioritizer.send(MetadataPushFrame(ByteReadPacket.Empty))
        prioritizer.send(KeepAliveFrame(true, 0, ByteReadPacket.Empty))

        assertTrue(prioritizer.receive() is MetadataPushFrame)
        assertTrue(prioritizer.receive() is KeepAliveFrame)
    }

    @Test
    fun testPrioritization() = test {
        prioritizer.send(CancelFrame(5))
        prioritizer.send(MetadataPushFrame(ByteReadPacket.Empty))
        prioritizer.send(CancelFrame(1))
        prioritizer.send(MetadataPushFrame(ByteReadPacket.Empty))

        assertEquals(0, prioritizer.receive().streamId)
        assertEquals(0, prioritizer.receive().streamId)
        assertEquals(5, prioritizer.receive().streamId)
        assertEquals(1, prioritizer.receive().streamId)
    }

    @Test
    fun testAsyncReceive() = test {
        val deferred = CompletableDeferred<Frame>()
        launch(anotherDispatcher) {
            deferred.complete(prioritizer.receive())
        }
        delay(100)
        prioritizer.send(CancelFrame(5))
        assertTrue(deferred.await() is CancelFrame)
    }

    @Test
    fun testPrioritizationAndOrdering() = test {
        prioritizer.send(RequestNFrame(1, 1))
        prioritizer.send(MetadataPushFrame(ByteReadPacket.Empty))
        prioritizer.send(CancelFrame(1))
        prioritizer.send(KeepAliveFrame(true, 0, ByteReadPacket.Empty))

        assertTrue(prioritizer.receive() is MetadataPushFrame)
        assertTrue(prioritizer.receive() is KeepAliveFrame)
        assertTrue(prioritizer.receive() is RequestNFrame)
        assertTrue(prioritizer.receive() is CancelFrame)
    }

    @Test
    fun testReleaseOnClose() = test {
        val packet = packet("metadata")
        val payload = payload("data")
        prioritizer.send(MetadataPushFrame(packet))
        prioritizer.send(NextPayloadFrame(1, payload))

        assertTrue(packet.isNotEmpty)
        assertTrue(payload.data.isNotEmpty)

        prioritizer.close(null)

        assertTrue(packet.isEmpty)
        assertTrue(payload.data.isEmpty)
    }
}
