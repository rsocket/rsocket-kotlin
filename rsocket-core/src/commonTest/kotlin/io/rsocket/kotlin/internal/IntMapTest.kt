/*
 * Copyright 2015-2022 the original author or authors.
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

import kotlin.random.*
import kotlin.test.*

class IntMapTest {
    private data class Value(private val name: String?)

    private val map = IntMap<Value>()

    @Test
    fun putNewMappingShouldSucceed() {
        val v = Value("v")
        val key = 1
        assertNull(map.set(key, v))
        assertEquals(1, map.size)
        assertTrue(map.contains(key))
        assertEquals(v, map[key])
    }

    @Test
    fun putShouldReplaceValue() {
        val v1 = Value("v1")
        val key = 1
        assertNull(map.set(key, v1))

        // Replace the value.
        val v2 = Value("v2")
        assertSame(v1, map.set(key, v2))
        assertEquals(1, map.size.toLong())
        assertTrue(map.contains(key))
        assertEquals(v2, map[key])
    }

    @Test
    fun setShouldGrowMap() {
        for (key in 0 until 255) {
            val v = Value(key.toString())
            assertNull(map.set(key, v))
            assertEquals(key + 1.toLong(), map.size.toLong())
            assertTrue(map.contains(key))
            assertEquals(v, map[key])
        }
    }

    @Test
    fun negativeKeyShouldSucceed() {
        val v = Value("v")
        map[-3] = v
        assertEquals(1, map.size.toLong())
        assertEquals(v, map[-3])
    }

    @Test
    fun removeMissingValueShouldReturnNull() {
        assertNull(map.remove(1))
        assertEquals(0, map.size.toLong())
    }

    @Test
    fun removeShouldReturnPreviousValue() {
        val v = Value("v")
        val key = 1
        map[key] = v
        assertSame(v, map.remove(key))
    }

    /**
     * This test is a bit internal-centric. We're just forcing a rehash to occur based on no longer
     * having any FREE slots available. We do this by adding and then removing several keys up to
     * the capacity, so that no rehash is done. We then add one more, which will cause the rehash
     * due to a lack of free slots and verify that everything is still behaving properly
     */
    @Test
    fun noFreeSlotsShouldRehash() {
        for (i in 0..9) {
            map[i] = Value(i.toString())
            // Now mark it as REMOVED so that size won't cause the rehash.
            map.remove(i)
            assertEquals(0, map.size.toLong())
        }

        // Now add an entry to force the rehash since no FREE slots are available in the map.
        val v = Value("v")
        val key = 1
        map[key] = v
        assertEquals(1, map.size.toLong())
        assertSame(v, map[key])
    }

    @Test
    fun clearShouldSucceed() {
        val v1 = Value("v1")
        val v2 = Value("v2")
        val v3 = Value("v3")
        map[1] = v1
        map[2] = v2
        map[3] = v3
        map.clear()
        assertEquals(0, map.size.toLong())
        assertTrue(map.size == 0)
    }

    @Test
    fun keysShouldBeReturned() {
        val k1 = 1
        val k2 = 2
        val k3 = 3
        val k4 = 4
        map[k1] = Value("v1")
        map[k2] = Value("v2")
        map[k3] = Value("v3")

        // Add and then immediately remove another entry.
        map[k4] = Value("v4")
        map.remove(k4)
        val keys = map.keys()
        assertEquals(3, keys.size)
        val expected = setOf(k1, k2, k3)
        val found = mutableSetOf<Int>()
        for (key in keys) {
            assertTrue(found.add(key))
        }
        assertEquals(expected, found)
    }

    @Test
    fun valuesShouldBeReturned() {
        val k1 = 1
        val k2 = 2
        val k3 = 3
        val k4 = 4
        val v1 = Value("v1")
        val v2 = Value("v2")
        val v3 = Value("v3")
        map[k1] = v1
        map[k2] = v2
        map[k3] = v3

        // Add and then immediately remove another entry.
        map[k4] = Value("v4")
        map.remove(k4)

        // Ensure values() return all values.
        val expected = setOf(v1, v2, v3)
        val actual = map.values().toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun mapShouldSupportHashingConflicts() {
        for (mod in 0..9) {
            var sz = 1
            while (sz <= 101) {
                val map = IntMap<String>(sz)
                for (i in 0..99) map[(i * mod)] = ""
                sz += 2
            }
        }
    }

    @Test
    fun fuzzTest() {
        // This test is so extremely internals-dependent that I'm not even trying to
        // minimize that. Any internal changes will not fail the test (so it's not flaky per se)
        // but will possibly make it less effective (not test interesting scenarios anymore).

        // The RNG algorithm is specified and stable, so this will cause the same exact dataset
        // to be used in every run and every JVM implementation.
        val rnd = Random(0)
        val baseSize = 1000
        // Empirically-determined size to expand the capacity exactly once, and before
        // the step that creates the long conflict chain. We need to test rehash(),
        // but also control when rehash happens because it cleans up the REMOVED entries.
        // This size is also chosen so after the single rehash, the map will be densely
        // populated, getting close to a second rehash but not triggering it.
        val startTableSize = 1105
        val map = IntMap<Int>(startTableSize)
        // Reference map which implementation we trust to be correct, will mirror all operations.
        val goodMap = HashMap<Int, Int>()

        // Add initial population.
        for (i in 0 until baseSize / 4) {
            var key = rnd.nextInt(baseSize)
            assertEquals(goodMap.put(key, key), map.set(key, key))
            // 50% elements are multiple of a divisor of startTableSize => more conflicts.
            key = (rnd.nextInt(baseSize) * 17)
            assertEquals(goodMap.put(key, key), map.set(key, key))
        }

        // Now do some mixed adds and removes for further fuzzing
        // Rehash will happen here, but only once, and the final size will be closer to max.
        for (i in 0 until baseSize * 1000) {
            val key = rnd.nextInt(baseSize)
            if (rnd.nextDouble() >= 0.2) {
                assertEquals(goodMap.put(key, key), map.set(key, key))
            } else {
                assertEquals(goodMap.remove(key), map.remove(key))
            }
        }

        // Final batch of fuzzing, only searches and removes.
        var removeSize: Int = map.size / 2
        while (removeSize > 0) {
            val key = rnd.nextInt(baseSize)
            val found = goodMap.contains(key)
            assertEquals(found, map.contains(key))
            assertEquals(goodMap.remove(key), map.remove(key))
            if (found) {
                --removeSize
            }
        }

        // Now gotta write some code to compare the final maps, as equals() won't work.
        assertEquals(goodMap.size, map.size)
        val goodKeys = goodMap.keys.toTypedArray().sortedArray()
        val keys = map.keys().toTypedArray().sortedArray()
        assertEquals(goodKeys.size, keys.size)
        for (i in goodKeys.indices) {
            assertEquals(goodKeys[i], keys[i])
        }

        // Finally drain the map.
        for (key in keys) {
            assertEquals(goodMap.remove(key), map.remove(key))
        }
        assertTrue(map.size == 0)
    }

}
