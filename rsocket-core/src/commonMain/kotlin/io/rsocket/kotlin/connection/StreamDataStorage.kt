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

package io.rsocket.kotlin.connection

import io.rsocket.kotlin.internal.*
import kotlinx.atomicfu.locks.*

internal class StreamDataStorage<T : Any>(private val isClient: Boolean) {
    private val lock = SynchronizedObject()
    private val streamIdGenerator: StreamIdGenerator = StreamIdGenerator(isClient)
    private val storage: IntMap<T> = IntMap()

    fun createStream(value: T): Int = synchronized(lock) {
        val streamId = streamIdGenerator.next(storage)
        storage[streamId] = value
        streamId
    }

    // false if not valid
    fun isValidForAccept(id: Int): Boolean {
        if (isClient.xor(id % 2 == 0)) return false
        return synchronized(lock) { id !in storage }
    }

    // false if not valid
    // TODO: implement IntMap.putIfAbsent
    fun acceptStream(id: Int, value: T): Boolean {
        if (isClient.xor(id % 2 == 0)) return false
        return synchronized(lock) {
            val isValid = id !in storage
            if (isValid) storage[id] = value
            isValid
        }
    }

    // for responder part of sequential connection
    fun replaceStream(id: Int, value: T): T? = synchronized(lock) { storage.set(id, value) }

    fun removeStream(id: Int): T? = synchronized(lock) { storage.remove(id) }
    fun getStream(id: Int): T? = synchronized(lock) { storage[id] }

    fun clear(): List<T> = synchronized(lock) {
        val values = storage.values()
        storage.clear()
        values
    }
}
