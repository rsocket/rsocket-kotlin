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

import kotlinx.atomicfu.*

internal actual class MutableValue<V : Any> actual constructor(initial: V) {
    private val _value = atomic(initial)
    actual val value: V get() = _value.value
    actual val update: (V) -> Unit = { _value.value = it }
}

internal actual class ValueStore<V : Any> actual constructor(size: Int, capacity: Int) {
    private val _size: AtomicInt = atomic(size)
    private val keys: AtomicIntArray = AtomicIntArray(capacity)
    private val values: AtomicArray<V?> = atomicArrayOfNulls(capacity)

    actual val size: Int get() = _size.value

    actual fun key(index: Int): Int = keys[index].value

    actual fun value(index: Int): V? = values[index].value

    actual fun setKey(index: Int, key: Int) {
        keys[index].value = key
    }

    actual fun setValue(index: Int, value: V?) {
        values[index].value = value
    }

    actual fun incrementSize(): Int = _size.incrementAndGet()
    actual fun decrementSize(): Int = _size.decrementAndGet()
    actual fun clearSize() {
        _size.value = 0
    }
}
