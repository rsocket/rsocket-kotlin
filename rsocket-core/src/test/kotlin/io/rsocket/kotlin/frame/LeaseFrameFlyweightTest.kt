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

package io.rsocket.kotlin.frame

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.internal.frame.LeaseFrameFlyweight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class LeaseFrameFlyweightTest {
    private val byteBuf = Unpooled.buffer(1024)

    @Test
    fun size() {
        val metadata = Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3, 4))
        val length = LeaseFrameFlyweight.encode(byteBuf, 0, 0, metadata)
        assertEquals(length.toLong(), (9 + 4 * 2 + 4).toLong()) // Frame header + ttl + #requests + 4 byte metadata
    }

    @Test
    fun testEncoding() {
        val encoded = LeaseFrameFlyweight.encode(
                byteBuf, 0, 0, Unpooled.copiedBuffer("md", StandardCharsets.UTF_8))
        assertEquals(
                "00001000000000090000000000000000006d64", ByteBufUtil.hexDump(byteBuf, 0, encoded))
    }
}
