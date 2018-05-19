package io.rsocket.kotlin

import org.junit.Assert.assertEquals

import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight
import org.junit.Test

class FrameTest {
    @Test
    fun testFrameToString() {
        val requestFrame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload("streaming in -> 0"), 1)
        assertEquals(
                "Frame => Stream ID: 1 Type: REQUEST_RESPONSE Payload: data: \"streaming in -> 0\" ",
                requestFrame.toString())
    }

    @Test
    fun testFrameWithMetadataToString() {
        val requestFrame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, DefaultPayload("streaming in -> 0", "metadata"), 1)
        assertEquals(
                "Frame => Stream ID: 1 Type: REQUEST_RESPONSE Payload: metadata: \"metadata\" data: \"streaming in -> 0\" ",
                requestFrame.toString())
    }

    @Test
    fun testPayload() {
        val frame = Frame.PayloadFrame.from(
                1, FrameType.NEXT_COMPLETE, DefaultPayload("Hello"), FrameHeaderFlyweight.FLAGS_C)
        frame.toString()
    }
}
