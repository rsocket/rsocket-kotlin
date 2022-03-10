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
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.payload.*
import io.rsocket.kotlin.test.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

class SetupFrameTest : TestWithLeakCheck {

    private val version = Version.Current
    private val keepAliveIntervalMillis = 10.seconds.toInt(DurationUnit.MILLISECONDS)
    private val keepAliveMaxLifetimeMillis = 500.seconds.toInt(DurationUnit.MILLISECONDS)
    private val metadataMimeTypeText = "mime"
    private val dataMimeTypeText = WellKnownMimeType.ApplicationOctetStream.text

    @Test
    fun testNoResumeEmptyPayload() {
        val frame = SetupFrame(
            version = version,
            honorLease = true,
            keepAliveIntervalMillis = keepAliveIntervalMillis,
            keepAliveMaxLifetimeMillis = keepAliveMaxLifetimeMillis,
            resumeToken = null,
            metadataMimeTypeText = metadataMimeTypeText,
            dataMimeTypeText = dataMimeTypeText,
            payload = Payload.Empty,
        )
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAliveIntervalMillis, decodedFrame.keepAliveIntervalMillis)
        assertEquals(keepAliveMaxLifetimeMillis, decodedFrame.keepAliveMaxLifetimeMillis)
        assertNull(decodedFrame.resumeToken)
        assertEquals(dataMimeTypeText, decodedFrame.dataMimeTypeText)
        assertEquals(metadataMimeTypeText, decodedFrame.metadataMimeTypeText)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testNoResumeBigPayload() {
        val payload = payload(ByteArray(30000) { 1 }, ByteArray(20000) { 5 })
        val frame = SetupFrame(
            version = version,
            honorLease = true,
            keepAliveIntervalMillis = keepAliveIntervalMillis,
            keepAliveMaxLifetimeMillis = keepAliveMaxLifetimeMillis,
            resumeToken = null,
            metadataMimeTypeText = metadataMimeTypeText,
            dataMimeTypeText = dataMimeTypeText,
            payload = payload
        )
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAliveIntervalMillis, decodedFrame.keepAliveIntervalMillis)
        assertEquals(keepAliveMaxLifetimeMillis, decodedFrame.keepAliveMaxLifetimeMillis)
        assertNull(decodedFrame.resumeToken)
        assertEquals(dataMimeTypeText, decodedFrame.dataMimeTypeText)
        assertEquals(metadataMimeTypeText, decodedFrame.metadataMimeTypeText)
        assertBytesEquals(ByteArray(30000) { 1 }, decodedFrame.payload.data.readBytes())
        assertBytesEquals(ByteArray(20000) { 5 }, decodedFrame.payload.metadata?.readBytes())
    }

    @Test
    fun testResumeBigTokenEmptyPayload() {
        val resumeToken = packet(ByteArray(65000) { 5 })
        val frame = SetupFrame(
            version = version,
            honorLease = true,
            keepAliveIntervalMillis = keepAliveIntervalMillis,
            keepAliveMaxLifetimeMillis = keepAliveMaxLifetimeMillis,
            resumeToken = resumeToken,
            metadataMimeTypeText = metadataMimeTypeText,
            dataMimeTypeText = dataMimeTypeText,
            payload = Payload.Empty
        )
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAliveIntervalMillis, decodedFrame.keepAliveIntervalMillis)
        assertEquals(keepAliveMaxLifetimeMillis, decodedFrame.keepAliveMaxLifetimeMillis)
        assertBytesEquals(ByteArray(65000) { 5 }, decodedFrame.resumeToken?.readBytes())
        assertEquals(dataMimeTypeText, decodedFrame.dataMimeTypeText)
        assertEquals(metadataMimeTypeText, decodedFrame.metadataMimeTypeText)
        assertEquals(0, decodedFrame.payload.data.remaining)
        assertNull(decodedFrame.payload.metadata)
    }

    @Test
    fun testResumeBigTokenBigPayload() {
        val resumeToken = packet(ByteArray(65000) { 5 })
        val payload = payload(ByteArray(30000) { 1 }, ByteArray(20000) { 5 })
        val frame = SetupFrame(
            version = version,
            honorLease = true,
            keepAliveIntervalMillis = keepAliveIntervalMillis,
            keepAliveMaxLifetimeMillis = keepAliveMaxLifetimeMillis,
            resumeToken = resumeToken,
            metadataMimeTypeText = metadataMimeTypeText,
            dataMimeTypeText = dataMimeTypeText,
            payload = payload
        )
        val decodedFrame = frame.loopFrame()

        assertTrue(decodedFrame is SetupFrame)
        assertEquals(0, decodedFrame.streamId)
        assertEquals(version, decodedFrame.version)
        assertTrue(decodedFrame.honorLease)
        assertEquals(keepAliveIntervalMillis, decodedFrame.keepAliveIntervalMillis)
        assertEquals(keepAliveMaxLifetimeMillis, decodedFrame.keepAliveMaxLifetimeMillis)
        assertBytesEquals(ByteArray(65000) { 5 }, decodedFrame.resumeToken?.readBytes())
        assertEquals(dataMimeTypeText, decodedFrame.dataMimeTypeText)
        assertEquals(metadataMimeTypeText, decodedFrame.metadataMimeTypeText)
        assertBytesEquals(ByteArray(30000) { 1 }, decodedFrame.payload.data.readBytes())
        assertBytesEquals(ByteArray(20000) { 5 }, decodedFrame.payload.metadata?.readBytes())
    }

}
