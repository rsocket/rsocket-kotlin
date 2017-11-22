/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.android.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.rsocket.android.Frame
import io.rsocket.android.FrameType
import io.rsocket.android.util.PayloadImpl
import java.nio.charset.StandardCharsets
import org.junit.Test

class RequestFrameFlyweightTest {
    private val byteBuf = Unpooled.buffer(1024)

    @Test
    fun testEncoding() {
        val encoded = RequestFrameFlyweight.encode(
                byteBuf,
                1,
                FrameHeaderFlyweight.FLAGS_M,
                FrameType.REQUEST_STREAM,
                1,
                Unpooled.copiedBuffer("md", StandardCharsets.UTF_8),
                Unpooled.copiedBuffer("d", StandardCharsets.UTF_8))
        assertEquals(
                "000010000000011900000000010000026d6464", ByteBufUtil.hexDump(byteBuf, 0, encoded))

        val payload = PayloadImpl(
                Frame.from(stringToBuf("000010000000011900000000010000026d6464")))

        assertEquals("md", StandardCharsets.UTF_8.decode(payload.metadata).toString())
    }

    @Test
    fun testEncodingWithEmptyMetadata() {
        val encoded = RequestFrameFlyweight.encode(
                byteBuf,
                1,
                FrameHeaderFlyweight.FLAGS_M,
                FrameType.REQUEST_STREAM,
                1,
                Unpooled.copiedBuffer("", StandardCharsets.UTF_8),
                Unpooled.copiedBuffer("d", StandardCharsets.UTF_8))
        assertEquals("00000e0000000119000000000100000064", ByteBufUtil.hexDump(byteBuf, 0, encoded))

        val payload = PayloadImpl(Frame.from(stringToBuf("00000e0000000119000000000100000064")))

        assertEquals("", StandardCharsets.UTF_8.decode(payload.metadata).toString())
    }

    @Test
    fun testEncodingWithNullMetadata() {
        val encoded = RequestFrameFlyweight.encode(
                byteBuf,
                1,
                0,
                FrameType.REQUEST_STREAM,
                1,
                null,
                Unpooled.copiedBuffer("d", StandardCharsets.UTF_8))
        assertEquals("00000b0000000118000000000164", ByteBufUtil.hexDump(byteBuf, 0, encoded))

        val payload = PayloadImpl(Frame.from(stringToBuf("00000b0000000118000000000164")))

        assertFalse(payload.hasMetadata())
    }

    private fun bufToString(encoded: Int): String {
        return ByteBufUtil.hexDump(byteBuf, 0, encoded)
    }

    private fun stringToBuf(s: CharSequence): ByteBuf {
        return Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(s))
    }
}
