/*
 * Copyright 2015-2022 the original author or authors.
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

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.test.*
import kotlin.test.*

class LeaseFrameTest : TestWithLeakCheck {

    private val ttl = 1
    private val numberOfRequests = 42
    private val metadata = "METADATA"

    @Test
    fun testMetadata() {
        val frame = LeaseFrame(ttl, numberOfRequests, packet(metadata))
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is LeaseFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(ttl, decodedFrame.ttl)
        assertEquals(numberOfRequests, decodedFrame.numberOfRequests)
        assertEquals(metadata, decodedFrame.metadata?.readText())
    }

    @Test
    fun testNoMetadata() {
        val frame = LeaseFrame(ttl, numberOfRequests, null)
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is LeaseFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(ttl, decodedFrame.ttl)
        assertEquals(numberOfRequests, decodedFrame.numberOfRequests)
        assertNull(decodedFrame.metadata)
    }
}
