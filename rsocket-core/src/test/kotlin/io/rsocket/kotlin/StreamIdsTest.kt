/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin

import io.rsocket.kotlin.internal.ClientStreamIds
import io.rsocket.kotlin.internal.ServerStreamIds
import io.rsocket.kotlin.internal.StreamIds
import io.rsocket.kotlin.internal.TestStreamIds
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class StreamIdsTest {

    @Test
    fun testClientSequence() {
        val map = Collections.emptyMap<Int, Any>()
        val s = ClientStreamIds()
        assertEquals(1, s.nextStreamId(map).toLong())
        assertEquals(3, s.nextStreamId(map).toLong())
        assertEquals(5, s.nextStreamId(map).toLong())
    }

    @Test
    fun testServerSequence() {
        val map = Collections.emptyMap<Int, Any>()
        val s = ServerStreamIds()
        assertEquals(2, s.nextStreamId(map).toLong())
        assertEquals(4, s.nextStreamId(map).toLong())
        assertEquals(6, s.nextStreamId(map).toLong())
    }

    @Test
    fun testClientSequenceWrap() {
        val map = ConcurrentHashMap<Int, Any>()
        val s = TestStreamIds(Integer.MAX_VALUE - 2)

        assertEquals(2147483647, s.nextStreamId(map).toLong())
        assertEquals(1, s.nextStreamId(map).toLong())
        assertEquals(3, s.nextStreamId(map).toLong())
    }

    @Test
    fun testServerSequenceWrap() {
        val map = ConcurrentHashMap<Int, Any>()
        val s = TestStreamIds(Integer.MAX_VALUE - 3)

        assertEquals(2147483646, s.nextStreamId(map).toLong())
        assertEquals(2, s.nextStreamId(map).toLong())
        assertEquals(4, s.nextStreamId(map).toLong())
    }

    @Test
    fun testSequenceSkipsExistingStreamIds() {
        val map = ConcurrentHashMap<Int, Any>()
        map.put(5, Any())
        map.put(9, Any())
        val s = TestStreamIds(StreamIds.MAX_STREAM_ID)
        assertEquals(1, s.nextStreamId(map).toLong())
        assertEquals(3, s.nextStreamId(map).toLong())
        assertEquals(7, s.nextStreamId(map).toLong())
        assertEquals(11, s.nextStreamId(map).toLong())
    }
}
