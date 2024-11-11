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

package io.rsocket.kotlin.transport.internal

import io.rsocket.kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.test.*

class PrioritizationFrameQueueTest : SuspendTest {
    private val queue = PrioritizationFrameQueue(Channel.BUFFERED)

    @Test
    fun testOrdering() = test {
        queue.enqueueFrame(1, packet("1"))
        queue.enqueueFrame(2, packet("2"))
        queue.enqueueFrame(3, packet("3"))

        assertEquals("1", queue.dequeueFrame()?.readString())
        assertEquals("2", queue.dequeueFrame()?.readString())
        assertEquals("3", queue.dequeueFrame()?.readString())
    }

    @Test
    fun testOrderingPriority() = test {
        queue.enqueueFrame(0, packet("1"))
        queue.enqueueFrame(0, packet("2"))

        assertEquals("1", queue.dequeueFrame()?.readString())
        assertEquals("2", queue.dequeueFrame()?.readString())
    }

    @Test
    fun testPrioritization() = test {
        queue.enqueueFrame(5, packet("1"))
        queue.enqueueFrame(0, packet("2"))
        queue.enqueueFrame(1, packet("3"))
        queue.enqueueFrame(0, packet("4"))

        assertEquals("2", queue.dequeueFrame()?.readString())
        assertEquals("4", queue.dequeueFrame()?.readString())

        assertEquals("1", queue.dequeueFrame()?.readString())
        assertEquals("3", queue.dequeueFrame()?.readString())
    }

    @Test
    fun testAsyncReceive() = test {
        val deferred = CompletableDeferred<Source?>()
        launch(anotherDispatcher) {
            deferred.complete(queue.dequeueFrame())
        }
        delay(100)
        queue.enqueueFrame(5, packet("1"))
        assertEquals("1", deferred.await()?.readString())
    }

    @Test
    fun testReleaseOnCancel() = test {
        val p1 = packet("1")
        val p2 = packet("2")
        queue.enqueueFrame(0, p1)
        queue.enqueueFrame(1, p2)

        assertTrue(!p1.exhausted())
        assertTrue(!p2.exhausted())

        queue.close()

        assertTrue(!p1.exhausted())
        assertTrue(!p2.exhausted())

        queue.cancel()

        assertTrue(p1.exhausted())
        assertTrue(p2.exhausted())
    }
}
