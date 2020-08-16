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

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.payload.*
import kotlin.test.*

class RequestFireAndForgetFrameTest {

    @Test
    fun testData() {
        val frame = RequestFireAndForgetFrame(3, Payload("d"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestFnF, decodedFrame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertEquals(null, decodedFrame.payload.metadata)
    }

    @Test
    fun testDataMetadata() {
        val frame = RequestFireAndForgetFrame(3, Payload("d", "md"))
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is RequestFrame)
        assertEquals(FrameType.RequestFnF, decodedFrame.type)
        assertEquals(3, decodedFrame.streamId)
        assertFalse(decodedFrame.follows)
        assertFalse(decodedFrame.complete)
        assertFalse(decodedFrame.next)
        assertEquals("d", decodedFrame.payload.data.readText())
        assertEquals("md", decodedFrame.payload.metadata?.readText())
    }

}
