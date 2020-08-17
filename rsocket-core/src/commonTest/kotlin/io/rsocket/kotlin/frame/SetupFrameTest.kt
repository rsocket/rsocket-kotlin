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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import kotlin.test.*
import kotlin.time.*

@OptIn(ExperimentalTime::class)
class SetupFrameTest {

    private val version = Version.Current
    private val keepAlive = KeepAlive(10.seconds, 500.seconds)
    private val payloadMimeType = PayloadMimeType("")

    @Test
    fun testNoResumeEmptyPayload() {
        val frame = SetupFrame(version, true, keepAlive, null, payloadMimeType, Payload.Empty)
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive, decodedFrame.keepAlive)
        assertNull(decodedFrame.resumeToken)
        assertEquals(payloadMimeType, decodedFrame.payloadMimeType)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNoResumeBigPayload() {
        val payload = Payload(ByteArray(30000) { 1 }, ByteArray(20000) { 5 })
        val frame = SetupFrame(version, true, keepAlive, null, payloadMimeType, payload)
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive, decodedFrame.keepAlive)
        assertNull(decodedFrame.resumeToken)
        assertEquals(payloadMimeType, decodedFrame.payloadMimeType)
        assertBytesEquals(ByteArray(30000) { 1 }, decodedFrame.payload.data.readBytes())
        assertBytesEquals(ByteArray(20000) { 5 }, decodedFrame.payload.metadata?.readBytes())
    }

    @Test
    fun testResumeBigTokenEmptyPayload() {
        val resumeToken = ByteReadPacket(ByteArray(65000) { 5 })
        val frame = SetupFrame(version, true, keepAlive, resumeToken, payloadMimeType, Payload.Empty)
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive, decodedFrame.keepAlive)
        assertBytesEquals(ByteArray(65000) { 5 }, decodedFrame.resumeToken?.readBytes())
        assertEquals(payloadMimeType, decodedFrame.payloadMimeType)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testResumeBigTokenBigPayload() {
        val resumeToken = ByteReadPacket(ByteArray(65000) { 5 })
        val payload = Payload(ByteArray(30000) { 1 }, ByteArray(20000) { 5 })
        val frame = SetupFrame(version, true, keepAlive, resumeToken, payloadMimeType, payload)
        val decodedFrame = frame.toPacket().toFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive, decodedFrame.keepAlive)
        assertBytesEquals(ByteArray(65000) { 5 }, decodedFrame.resumeToken?.readBytes())
        assertEquals(payloadMimeType, decodedFrame.payloadMimeType)
        assertBytesEquals(ByteArray(30000) { 1 }, decodedFrame.payload.data.readBytes())
        assertBytesEquals(ByteArray(20000) { 5 }, decodedFrame.payload.metadata?.readBytes())
    }

}
