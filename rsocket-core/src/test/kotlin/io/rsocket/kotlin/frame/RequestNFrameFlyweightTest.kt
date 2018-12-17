/*
 * Copyright 2015-2018 the original author or authors.
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
import io.rsocket.kotlin.internal.frame.RequestNFrameFlyweight
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestNFrameFlyweightTest {
    private val byteBuf = Unpooled.buffer(1024)

    @Test
    fun testEncoding() {
        val encoded = RequestNFrameFlyweight.encode(byteBuf, 1, 5)
        assertEquals("00000a00000001200000000005", ByteBufUtil.hexDump(byteBuf, 0, encoded))
    }
}
