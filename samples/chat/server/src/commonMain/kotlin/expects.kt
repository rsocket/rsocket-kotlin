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

package io.rsocket.kotlin.samples.chat.server

import io.ktor.util.collections.*

expect class Counter() {
    fun next(): Int
}

expect fun currentMillis(): Long

class Storage<T : Any> {
    private val map: MutableMap<Int, T> = ConcurrentMap()
    private val id = Counter()

    fun nextId(): Int = id.next()

    operator fun get(id: Int): T = map.getValue(id)
    fun getOrNull(id: Int): T? = map[id]

    fun save(id: Int, value: T) {
        map[id] = value
    }

    fun remove(id: Int) {
        map.remove(id)
    }

    fun contains(id: Int): Boolean = id in map
    fun values(): List<T> = map.values.toList()

}