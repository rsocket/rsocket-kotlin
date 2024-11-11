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

package io.rsocket.kotlin.operation

import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.io.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.test.*

class OperationOutboundTest : SuspendTest {
    private class Outbound(
        streamId: Int,
        maxFragmentSize: Int,
    ) : OperationOutbound(streamId, FrameCodec(maxFragmentSize)) {
        val frames = bufferChannel(Channel.BUFFERED)
        override val isClosed: Boolean get() = frames.isClosedForSend

        override suspend fun sendFrame(frame: Buffer) {
            frames.send(frame)
        }
    }

    private fun sender(maxFragmentSize: Int) = Outbound(1, maxFragmentSize)

    @Test
    fun testFrameFragmented() = test {
        val sender = sender(99)

        sender.sendNext(buildPayload {
            data("1234567890".repeat(50))
        }, false)

        repeat(6) {
            val frame = sender.frames.receive().readFrame()
            assertIs<RequestFrame>(frame)
            assertTrue(frame.next)
            assertNull(frame.payload.metadata)
            if (it != 5) {
                assertTrue(frame.follows)
                assertEquals("1234567890".repeat(9), frame.payload.data.readString())
            } else { //last frame
                assertFalse(frame.follows)
                assertEquals("1234567890".repeat(5), frame.payload.data.readString())
            }
        }
    }

    @Test
    fun testFrameFragmentedFully() = test {
        val sender = sender(99)

        sender.sendNext(buildPayload {
            data("1234567890".repeat(18))
        }, false)

        repeat(2) {
            val frame = sender.frames.receive().readFrame()
            assertIs<RequestFrame>(frame)
            assertTrue(frame.next)
            assertNull(frame.payload.metadata)
            assertEquals("1234567890".repeat(9), frame.payload.data.readString())
            if (it != 1) {
                assertTrue(frame.follows)
            } else { //last frame
                assertFalse(frame.follows)
            }
        }
    }
}
