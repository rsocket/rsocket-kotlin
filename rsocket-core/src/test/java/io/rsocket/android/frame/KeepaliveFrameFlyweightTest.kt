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

import org.junit.Assert.*

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.rsocket.android.frame.FrameHeaderFlyweight
import io.rsocket.android.frame.KeepaliveFrameFlyweight
import java.nio.charset.StandardCharsets
import org.junit.Test

class KeepaliveFrameFlyweightTest {
    private val byteBuf = Unpooled.buffer(1024)

    @Test
    fun canReadData() {
        val data = Unpooled.wrappedBuffer(byteArrayOf(5, 4, 3))
        val length = KeepaliveFrameFlyweight.encode(byteBuf, KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R, data)
        data.resetReaderIndex()

        assertEquals(
                KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R.toLong(),
                (FrameHeaderFlyweight.flags(byteBuf) and KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R).toLong())
        assertEquals(data, FrameHeaderFlyweight.sliceFrameData(byteBuf))
    }

    @Test
    fun testEncoding() {
        val encoded = KeepaliveFrameFlyweight.encode(
                byteBuf,
                KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R,
                Unpooled.copiedBuffer("d", StandardCharsets.UTF_8))
        assertEquals("00000f000000000c80000000000000000064", ByteBufUtil.hexDump(byteBuf, 0, encoded))
    }
}
