package dev.whyoleg.rsocket.internal

import kotlin.test.*

class StreamIdTest {
    private val map = mutableMapOf<Int, String>()

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
