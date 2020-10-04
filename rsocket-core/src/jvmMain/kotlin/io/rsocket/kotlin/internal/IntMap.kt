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

internal actual class MutableValue<V : Any> actual constructor(initial: V) {
    private var _value = initial
    actual val value: V get() = _value
    actual val update: (V) -> Unit = { _value = it }
}

internal actual class ValueStore<V : Any> actual constructor(size: Int, capacity: Int) {
    private var _size = size
    private val keys: IntArray = IntArray(capacity)

    @Suppress("UNCHECKED_CAST")
    private val values: Array<V?> = arrayOfNulls<Any?>(capacity) as Array<V?>

    actual val size: Int get() = _size

    actual fun key(index: Int): Int = keys[index]

    actual fun value(index: Int): V? = values[index]

    actual fun setKey(index: Int, key: Int) {
        keys[index] = key
    }

    actual fun setValue(index: Int, value: V?) {
        values[index] = value
    }

    actual fun incrementSize(): Int {
        _size += 1
        return _size
    }

    actual fun decrementSize(): Int {
        _size -= 1
        return _size
    }

    actual fun clearSize() {
        _size = 0
    }
}
