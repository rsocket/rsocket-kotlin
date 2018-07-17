/*
 * Copyright 2015-2018 the original author or authors.
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
