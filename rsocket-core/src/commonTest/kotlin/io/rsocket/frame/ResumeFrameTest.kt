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

package io.rsocket.frame

import io.ktor.utils.io.core.*
import io.rsocket.frame.io.*
import kotlin.test.*

class ResumeFrameTest {

    private val version = Version.Current
    private val lastReceivedServerPosition = 21L
    private val firstAvailableClientPosition = 12L

    @Test
    fun testBigToken() {
        val token = ByteArray(65000) { 1 }
        val frame = ResumeFrame(version, ByteReadPacket(token), lastReceivedServerPosition, firstAvailableClientPosition)
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is ResumeFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertBytesEquals(token, decodedFrame.resumeToken.readBytes())
        assertEquals(lastReceivedServerPosition, decodedFrame.lastReceivedServerPosition)
        assertEquals(firstAvailableClientPosition, decodedFrame.firstAvailableClientPosition)
    }

    @Test
    fun testSmallToken() {
        val token = ByteArray(100) { 1 }
        val frame = ResumeFrame(version, ByteReadPacket(token), lastReceivedServerPosition, firstAvailableClientPosition)
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is ResumeFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertBytesEquals(token, decodedFrame.resumeToken.readBytes())
        assertEquals(lastReceivedServerPosition, decodedFrame.lastReceivedServerPosition)
        assertEquals(firstAvailableClientPosition, decodedFrame.firstAvailableClientPosition)
    }

}
