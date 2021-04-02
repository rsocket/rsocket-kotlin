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

package io.rsocket.kotlin.test

import kotlinx.atomicfu.*
import kotlin.math.*
import kotlin.native.*

actual fun identityHashCode(instance: Any): Int = instance.identityHashCode()

//implementation is based on SharedSet/SharedVector from ktor-io tests
//supports freezing
actual class MSet<T> {
    private var _size: Int by atomic(0)
    private var content by atomic(MVector<MVector<T>>())
    private val loadFactor: Float get() = _size.toFloat() / content.size

    init {
        initBuckets(8)
    }

    actual fun add(element: T): Boolean {
        val bucket = findBucket(element)

        if (bucket.find(element) >= 0) {
            return false
        }

        bucket.push(element)
        _size++

        if (loadFactor > 0.75) {
            doubleSize()
        }

        return true
    }

    actual fun remove(element: T): Boolean {
        val bucket = findBucket(element)
        val result = bucket.remove(element)

        if (result) {
            _size--
        }

        return result
    }

    private fun doubleSize() {
        val old = content
        initBuckets(content.size * 2)
        _size = 0

        for (bucketId in 0 until old.size) {
            val bucket = old[bucketId]
            for (itemId in 0 until bucket.size) {
                add(bucket[itemId])
            }
        }
    }

    private fun initBuckets(count: Int) {
        val newContent = MVector<MVector<T>>()
        repeat(count) {
            newContent.push(MVector())
        }

        content = newContent
    }

    private fun findBucket(element: T): MVector<T> = content[(element.hashCode().absoluteValue) % content.size]

    actual fun clear() {
        content.values().forEach(MVector<T>::clear)
        content.clear()
        initBuckets(8)
        _size = 0
    }

    actual fun values(): List<T> = content.values().flatMap(MVector<T>::values)
}


private class MVector<T> {
    private var _size by atomic(0)
    private var content: AtomicArray<T?> by atomic(atomicArrayOfNulls<T>(10))

    val size: Int get() = _size

    fun values(): List<T> = buildList {
        repeat(content.size) {
            content[it].value?.let(::add)
        }
    }

    fun push(element: T) {
        if (_size >= content.size) {
            increaseCapacity()
        }

        content[_size].value = element
        _size++
    }

    fun find(element: T): Int {
        for (index in 0 until _size) {
            if (element == content[index].value) {
                return index
            }
        }

        return -1
    }

    operator fun get(index: Int): T {
        if (index >= _size) {
            throw IndexOutOfBoundsException("Index: $index, size: $size.")
        }

        return content[index].value!!
    }

    fun remove(element: T): Boolean {
        val index = find(element)
        if (index < 0) {
            return false
        }

        if (index >= size) {
            throw IndexOutOfBoundsException("Index: $index, size: $size.")
        }

        for (current in index until _size - 1) {
            content[current].value = content[current + 1].value
        }

        content[_size - 1].value = null
        _size--
        return true
    }

    fun clear() {
        _size = 0
        content = atomicArrayOfNulls(0)
    }

    private fun increaseCapacity() {
        val newContent = atomicArrayOfNulls<T>(content.size * 2)
        for (index in 0 until content.size) {
            newContent[index].value = content[index].value
        }

        content = newContent
    }
}
