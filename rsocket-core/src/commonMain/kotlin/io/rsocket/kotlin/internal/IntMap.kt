/*
 * Copyright 2015-2024 the original author or authors.
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

import kotlin.math.*

private fun safeFindNextPositivePowerOfTwo(value: Int): Int = when {
    value <= 0          -> 1
    value >= 0x40000000 -> 0x40000000
    else                -> 1 shl 32 - (value - 1).countLeadingZeroBits()
}

// TODO: may be move to `internal-io` (and rename to just `rsocket-internal`)
//  and use in prioritization queue to support more granular prioritization for streams
//
// TODO decide, is it needed, or can be replaced by simple map, or concurrent map on JVM?
//  do benchmarks
/**
 * IntMap implementation based on Netty IntObjectHashMap.
 */
internal class IntMap<V : Any>(
    initialCapacity: Int = 8,
    private val loadFactor: Float = 0.5f,
) {
    init {
        require(loadFactor > 0.0f && loadFactor <= 1.0f) { "loadFactor must be > 0 and <= 1" }
    }

    var size: Int = 0
        private set

    private var capacity: Int = safeFindNextPositivePowerOfTwo(initialCapacity)
    private var mask: Int = capacity - 1

    // Clip the upper bound so that there will always be at least one available slot.
    private var maxSize = min(mask, (capacity * loadFactor).toInt())
    private var keys: IntArray = IntArray(capacity)

    @Suppress("UNCHECKED_CAST")
    private var values: Array<V?> = arrayOfNulls<Any?>(capacity) as Array<V?>

    //called when capacity is updated
    private fun init() {
        mask = capacity - 1

        // Clip the upper bound so that there will always be at least one available slot.
        maxSize = min(mask, (capacity * loadFactor).toInt())
        keys = IntArray(capacity)

        @Suppress("UNCHECKED_CAST")
        values = arrayOfNulls<Any?>(capacity) as Array<V?>
    }

    operator fun contains(key: Int): Boolean = indexOf(key) >= 0

    operator fun get(key: Int): V? {
        val index = indexOf(key)
        if (index == -1) return null
        return values[index]
    }

    fun remove(key: Int): V? {
        val index = indexOf(key)
        if (index == -1) return null
        val prev = values[index]
        removeAt(index)
        return prev
    }

    operator fun set(key: Int, value: V): V? {
        val startIndex = hashIndex(key)
        var index = startIndex
        while (true) {
            if (values[index] == null) {
                // Found empty slot, use it.
                set(index, key, value)
                grow()
                return null
            }
            if (keys[index] == key) {
                // Found existing entry with this key, just replace the value.
                val previousValue = values[index]
                values[index] = value
                return previousValue
            }

            // Conflict, keep probing ...
            index = probeNext(index)

            // Can only happen if the map was full at MAX_ARRAY_SIZE and couldn't grow.
            check(index != startIndex) { "Unable to insert" }
        }
    }

    fun clear() {
        repeat(capacity, this::clear)
        size = 0
    }

    fun values(): List<V> {
        val list = mutableListOf<V>()
        repeat(capacity) {
            values[it]?.let(list::add)
        }
        return list.toList()
    }

    fun keys(): Set<Int> {
        val set = mutableSetOf<Int>()
        repeat(capacity) {
            val key = keys[it]
            if (contains(key)) set.add(key)
        }
        return set.toSet()
    }

    private fun set(index: Int, key: Int, value: V?) {
        keys[index] = key
        values[index] = value
    }

    private fun clear(index: Int): Unit = set(index, 0, null)

    /**
     * Returns the hashed index for the given key.
     * The array lengths are always a power of two, so we can use a bitmask to stay inside the array bounds.
     */
    private fun hashIndex(key: Int): Int = key and mask

    /**
     * Get the next sequential index after `index` and wraps if necessary.
     * The array lengths are always a power of two, so we can use a bitmask to stay inside the array bounds.
     */
    private fun probeNext(index: Int): Int = index + 1 and mask

    /**
     * Locates the index for the given key. This method probes using double hashing.
     *
     * @param key the key for an entry in the map.
     * @return the index where the key was found, or `-1` if no entry is found for that key.
     */
    private fun indexOf(key: Int): Int {
        val startIndex = hashIndex(key)
        var index = startIndex
        while (true) {
            // It's available, so no chance that this value exists anywhere in the map.
            if (values[index] == null) return -1
            if (keys[index] == key) return index

            // Conflict, keep probing ...
            index = probeNext(index)
            if (index == startIndex) return -1
        }
    }

    /**
     * Removes entry at the given index position. Also performs opportunistic, incremental rehashing
     * if necessary to not break conflict chains.
     *
     * @param index the index position of the element to remove.
     * @return `true` if the next item was moved back. `false` otherwise.
     */
    private fun removeAt(index: Int): Boolean {
        size -= 1
        // Clearing the key is not strictly necessary (for GC like in a regular collection),
        // but recommended for security. The memory location is still fresh in the cache anyway.
        clear(index)

        // In the interval from index to the next available entry, the arrays may have entries
        // that are displaced from their base position due to prior conflicts. Iterate these
        // entries and move them back if possible, optimizing future lookups.
        // Knuth Section 6.4 Algorithm R, also used by the JDK's IdentityHashMap.
        var nextFree = index
        var i = probeNext(index)
        var value = values[i]
        while (value != null) {
            val key = keys[i]
            val bucket = hashIndex(key)
            if (i < bucket && (bucket <= nextFree || nextFree <= i) || bucket <= nextFree && nextFree <= i) {
                // Move the displaced entry "back" to the first available position.
                set(nextFree, key, value)
                // Put the first entry after the displaced entry
                clear(i)
                nextFree = i
            }
            i = probeNext(i)
            value = values[i]
        }
        return nextFree != index
    }

    /**
     * Grows the map size after an insertion. If necessary, performs a rehash of the map.
     */
    private fun grow() {
        size += 1
        if (size <= maxSize) return

        check(capacity != Int.MAX_VALUE) { "Max capacity reached at size=$size" }
        rehash()
    }

    /**
     * Rehashes the map for the given capacity.
     * Double the capacity.
     */
    private fun rehash() {

        val oldCapacity = capacity
        val oldValues = values
        val oldKeys = keys

        capacity = capacity shl 1
        init()

        // Insert to the new arrays.
        repeat(oldCapacity) {
            val oldValue = oldValues[it] ?: return@repeat
            val oldKey = oldKeys[it]
            var index = hashIndex(oldKey)
            while (true) {
                if (values[index] == null) {
                    set(index, oldKey, oldValue)
                    break
                }
                // Conflict, keep probing. Can wrap around, but never reaches startIndex again.
                index = probeNext(index)
            }
        }
    }
}
