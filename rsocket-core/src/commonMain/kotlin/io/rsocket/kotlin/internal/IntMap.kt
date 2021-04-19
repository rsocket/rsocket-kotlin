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

import kotlinx.atomicfu.locks.*
import kotlin.math.*

private fun safeFindNextPositivePowerOfTwo(value: Int): Int = when {
    value <= 0          -> 1
    value >= 0x40000000 -> 0x40000000
    else                -> 1 shl 32 - (value - 1).countLeadingZeroBits()
}

/**
 * Synchronized IntMap implementation based on Netty IntObjectHashMap.
 * Uses atomics on K/N to support mutation.
 * On JVM and JS no atomics used.
 */
internal class IntMap<V : Any>(initialCapacity: Int = 8, private val loadFactor: Float = 0.5f) : SynchronizedObject() {
    init {
        require(loadFactor > 0.0f && loadFactor <= 1.0f) { "loadFactor must be > 0 and <= 1" }
    }

    private val store = MutableValue(MapState(0, safeFindNextPositivePowerOfTwo(initialCapacity)))

    val size: Int get() = store.value.store.size

    private inline fun <T> sync(block: MapState.() -> T): T = synchronized(this) { block(store.value) }

    operator fun get(key: Int): V? = sync { get(key) }
    operator fun set(key: Int, value: V): V? = sync { setAndGrow(key, value, this@IntMap.store.update) }
    fun remove(key: Int): V? = sync { remove(key) }
    fun clear() = sync { clear() }
    operator fun contains(key: Int): Boolean = sync { contains(key) }
    fun values(): List<V> = sync { values() }
    fun keys(): Set<Int> = sync { keys() }

    private inner class MapState(size: Int, private val capacity: Int) {
        private val mask = capacity - 1

        // Clip the upper bound so that there will always be at least one available slot.
        private val maxSize = min(mask, (capacity * loadFactor).toInt())

        val store: ValueStore<V> = ValueStore(size, capacity)

        operator fun contains(key: Int): Boolean = indexOf(key) >= 0

        operator fun get(key: Int): V? {
            val index = indexOf(key)
            if (index == -1) return null
            return store.value(index)
        }

        fun remove(key: Int): V? {
            val index = indexOf(key)
            if (index == -1) return null
            val prev = store.value(index)
            removeAt(index)
            return prev
        }

        fun setAndGrow(key: Int, value: V, grow: (newState: MapState) -> Unit): V? {
            val startIndex = hashIndex(key)
            var index = startIndex
            while (true) {
                if (store.value(index) == null) {
                    // Found empty slot, use it.
                    set(index, key, value)
                    growSize(grow)
                    return null
                }
                if (store.key(index) == key) {
                    // Found existing entry with this key, just replace the value.
                    val previousValue = store.value(index)
                    store.setValue(index, value)
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
            store.clearSize()
        }

        fun values(): List<V> {
            val list = mutableListOf<V>()
            repeat(capacity) {
                store.value(it)?.let(list::add)
            }
            return list.toList()
        }

        fun keys(): Set<Int> {
            val set = mutableSetOf<Int>()
            repeat(capacity) {
                val key = store.key(it)
                if (this@MapState.contains(key)) set.add(key)
            }
            return set.toSet()
        }

        private fun set(index: Int, key: Int, value: V?) {
            store.setKey(index, key)
            store.setValue(index, value)
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
                if (store.value(index) == null) return -1
                if (store.key(index) == key) return index

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
            store.decrementSize()
            // Clearing the key is not strictly necessary (for GC like in a regular collection),
            // but recommended for security. The memory location is still fresh in the cache anyway.
            clear(index)

            // In the interval from index to the next available entry, the arrays may have entries
            // that are displaced from their base position due to prior conflicts. Iterate these
            // entries and move them back if possible, optimizing future lookups.
            // Knuth Section 6.4 Algorithm R, also used by the JDK's IdentityHashMap.
            var nextFree = index
            var i = probeNext(index)
            var value = store.value(i)
            while (value != null) {
                val key = store.key(i)
                val bucket = hashIndex(key)
                if (i < bucket && (bucket <= nextFree || nextFree <= i) || bucket <= nextFree && nextFree <= i) {
                    // Move the displaced entry "back" to the first available position.
                    set(nextFree, key, value)
                    // Put the first entry after the displaced entry
                    clear(i)
                    nextFree = i
                }
                i = probeNext(i)
                value = store.value(i)
            }
            return nextFree != index
        }

        /**
         * Grows the map size after an insertion. If necessary, performs a rehash of the map.
         */
        private fun growSize(grow: (newState: MapState) -> Unit) {
            val size = store.incrementSize()
            if (size <= maxSize) return

            check(capacity != Int.MAX_VALUE) { "Max capacity reached at size=$size" }
            grow(rehash())
        }

        /**
         * Rehashes the map for the given capacity.
         * Double the capacity.
         */
        private fun rehash(): MapState = MapState(store.size, capacity shl 1).also { newState ->
            // Insert to the new arrays.
            repeat(capacity) {
                val oldValue = store.value(it) ?: return@repeat
                val oldKey = store.key(it)
                var index = newState.hashIndex(oldKey)
                while (true) {
                    if (newState.store.value(index) == null) {
                        newState.set(index, oldKey, oldValue)
                        break
                    }
                    // Conflict, keep probing. Can wrap around, but never reaches startIndex again.
                    index = probeNext(index)
                }
            }
            clear()
        }
    }
}

internal expect class MutableValue<V : Any>(initial: V) {
    val value: V
    val update: (V) -> Unit
}

internal expect class ValueStore<V : Any>(size: Int, capacity: Int) {
    val size: Int
    fun key(index: Int): Int
    fun value(index: Int): V?
    fun setKey(index: Int, key: Int)
    fun setValue(index: Int, value: V?)
    fun incrementSize(): Int
    fun decrementSize(): Int
    fun clearSize()
}
