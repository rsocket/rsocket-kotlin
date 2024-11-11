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

package io.rsocket.kotlin.frame

import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class SetupFrameTest {

    private val version = Version.Current
    private val keepAlive = KeepAlive(10.seconds, 500.seconds)
    private val payloadMimeType = PayloadMimeType(WellKnownMimeType.ApplicationOctetStream, CustomMimeType("mime"))

    @Test
    fun testNoResumeEmptyPayload() {
        val frame = SetupFrame(version, true, keepAlive, null, payloadMimeType, Payload.Empty)
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive.intervalMillis, decodedFrame.keepAlive.intervalMillis)
        assertEquals(keepAlive.maxLifetimeMillis, decodedFrame.keepAlive.maxLifetimeMillis)
        assertNull(decodedFrame.resumeToken)
        assertEquals(payloadMimeType.data, decodedFrame.payloadMimeType.data)
        assertEquals(payloadMimeType.metadata, decodedFrame.payloadMimeType.metadata)
        assertTrue(decodedFrame.payload.data.exhausted())
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNoResumeBigPayload() {
        val payload = payload(ByteArray(30000) { 1 }, ByteArray(20000) { 5 })
        val frame = SetupFrame(version, true, keepAlive, null, payloadMimeType, payload)
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive.intervalMillis, decodedFrame.keepAlive.intervalMillis)
        assertEquals(keepAlive.maxLifetimeMillis, decodedFrame.keepAlive.maxLifetimeMillis)
        assertNull(decodedFrame.resumeToken)
        assertEquals(payloadMimeType.data, decodedFrame.payloadMimeType.data)
        assertEquals(payloadMimeType.metadata, decodedFrame.payloadMimeType.metadata)
        assertBytesEquals(ByteArray(30000) { 1 }, decodedFrame.payload.data.readByteArray())
        assertBytesEquals(ByteArray(20000) { 5 }, decodedFrame.payload.metadata?.readByteArray())
    }

    @Test
    fun testResumeBigTokenEmptyPayload() {
        val resumeToken = packet(ByteArray(65000) { 5 })
        val frame = SetupFrame(version, true, keepAlive, resumeToken, payloadMimeType, Payload.Empty)
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive.intervalMillis, decodedFrame.keepAlive.intervalMillis)
        assertEquals(keepAlive.maxLifetimeMillis, decodedFrame.keepAlive.maxLifetimeMillis)
        assertBytesEquals(ByteArray(65000) { 5 }, decodedFrame.resumeToken?.readByteArray())
        assertEquals(payloadMimeType.data, decodedFrame.payloadMimeType.data)
        assertEquals(payloadMimeType.metadata, decodedFrame.payloadMimeType.metadata)
        assertTrue(decodedFrame.payload.data.exhausted())
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testResumeBigTokenBigPayload() {
        val resumeToken = packet(ByteArray(65000) { 5 })
        val payload = payload(ByteArray(30000) { 1 }, ByteArray(20000) { 5 })
        val frame = SetupFrame(version, true, keepAlive, resumeToken, payloadMimeType, payload)
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAlive.intervalMillis, decodedFrame.keepAlive.intervalMillis)
        assertEquals(keepAlive.maxLifetimeMillis, decodedFrame.keepAlive.maxLifetimeMillis)
        assertBytesEquals(ByteArray(65000) { 5 }, decodedFrame.resumeToken?.readByteArray())
        assertEquals(payloadMimeType.data, decodedFrame.payloadMimeType.data)
        assertEquals(payloadMimeType.metadata, decodedFrame.payloadMimeType.metadata)
        assertBytesEquals(ByteArray(30000) { 1 }, decodedFrame.payload.data.readByteArray())
        assertBytesEquals(ByteArray(20000) { 5 }, decodedFrame.payload.metadata?.readByteArray())
    }

}
