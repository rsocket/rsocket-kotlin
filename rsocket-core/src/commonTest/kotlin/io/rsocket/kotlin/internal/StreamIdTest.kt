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

package io.rsocket.kotlin.internal

import kotlin.test.*

class StreamIdTest {
    private val map = IntMap<String>()

    @Test
    fun testClientSequence() {
        val streamId = StreamId.client()
        assertEquals(1, streamId.next(map))
        assertEquals(3, streamId.next(map))
        assertEquals(5, streamId.next(map))
    }

    @Test
    fun testServerSequence() {
        val streamId = StreamId.server()
        assertEquals(2, streamId.next(map))
        assertEquals(4, streamId.next(map))
        assertEquals(6, streamId.next(map))
    }

    @Test
    fun testClientIsValid() {
        val streamId = StreamId.client()
        assertFalse(streamId.isBeforeOrCurrent(1))
        assertFalse(streamId.isBeforeOrCurrent(3))
        streamId.next(map)
        assertTrue(streamId.isBeforeOrCurrent(1))
        assertFalse(streamId.isBeforeOrCurrent(3))
        streamId.next(map)
        assertTrue(streamId.isBeforeOrCurrent(3))
        // negative
        assertFalse(streamId.isBeforeOrCurrent(-1))
        // connection
        assertFalse(streamId.isBeforeOrCurrent(0))
        // server also accepted (checked externally)
        assertTrue(streamId.isBeforeOrCurrent(2))
    }

    @Test
    fun testServerIsValid() {
        val streamId = StreamId.server()
        assertFalse(streamId.isBeforeOrCurrent(2))
        assertFalse(streamId.isBeforeOrCurrent(4))
        streamId.next(map)
        assertTrue(streamId.isBeforeOrCurrent(2))
        assertFalse(streamId.isBeforeOrCurrent(4))
        streamId.next(map)
        assertTrue(streamId.isBeforeOrCurrent(4))
        // negative
        assertFalse(streamId.isBeforeOrCurrent(-2))
        // connection
        assertFalse(streamId.isBeforeOrCurrent(0))
        // client also accepted (checked externally)
        assertTrue(streamId.isBeforeOrCurrent(1))
    }

    @Test
    fun testWrapOdd() {
        val streamId = StreamId(Int.MAX_VALUE - 3)
        assertEquals(2147483646, streamId.next(map))
        assertEquals(2, streamId.next(map))
        assertEquals(4, streamId.next(map))
    }

    @Test
    fun testWrapEven() {
        val streamId = StreamId(Int.MAX_VALUE - 2)
        assertEquals(2147483647, streamId.next(map))
        assertEquals(1, streamId.next(map))
        assertEquals(3, streamId.next(map))
    }

    @Test
    fun testSkipFound() {
        val streamId = StreamId.client()
        map[5] = ""
        map[9] = ""
        assertEquals(1, streamId.next(map))
        assertEquals(3, streamId.next(map))
        assertEquals(7, streamId.next(map))
        assertEquals(11, streamId.next(map))
    }
}
