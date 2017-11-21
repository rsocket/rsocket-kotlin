/*
 * Copyright 2016 Netflix, Inc.
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

package io.rsocket.android

import io.rsocket.StreamIdSupplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

import org.junit.Test

class StreamIdSupplierTest {
    @Test
    fun testClientSequence() {
        val s = StreamIdSupplier.clientSupplier()
        assertEquals(1, s.nextStreamId().toLong())
        assertEquals(3, s.nextStreamId().toLong())
        assertEquals(5, s.nextStreamId().toLong())
    }

    @Test
    fun testServerSequence() {
        val s = StreamIdSupplier.serverSupplier()
        assertEquals(2, s.nextStreamId().toLong())
        assertEquals(4, s.nextStreamId().toLong())
        assertEquals(6, s.nextStreamId().toLong())
    }

    @Test
    fun testClientIsValid() {
        val s = StreamIdSupplier.clientSupplier()

        assertFalse(s.isBeforeOrCurrent(1))
        assertFalse(s.isBeforeOrCurrent(3))

        s.nextStreamId()
        assertTrue(s.isBeforeOrCurrent(1))
        assertFalse(s.isBeforeOrCurrent(3))

        s.nextStreamId()
        assertTrue(s.isBeforeOrCurrent(3))

        // negative
        assertFalse(s.isBeforeOrCurrent(-1))
        // connection
        assertFalse(s.isBeforeOrCurrent(0))
        // server also accepted (checked externally)
        assertTrue(s.isBeforeOrCurrent(2))
    }

    @Test
    fun testServerIsValid() {
        val s = StreamIdSupplier.serverSupplier()

        assertFalse(s.isBeforeOrCurrent(2))
        assertFalse(s.isBeforeOrCurrent(4))

        s.nextStreamId()
        assertTrue(s.isBeforeOrCurrent(2))
        assertFalse(s.isBeforeOrCurrent(4))

        s.nextStreamId()
        assertTrue(s.isBeforeOrCurrent(4))

        // negative
        assertFalse(s.isBeforeOrCurrent(-2))
        // connection
        assertFalse(s.isBeforeOrCurrent(0))
        // client also accepted (checked externally)
        assertTrue(s.isBeforeOrCurrent(1))
    }
}
