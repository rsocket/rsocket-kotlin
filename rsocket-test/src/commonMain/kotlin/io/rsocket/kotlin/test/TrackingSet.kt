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

import io.ktor.utils.io.core.internal.*
import kotlinx.atomicfu.locks.*

class TrackingSet : SynchronizedObject() {
    private val set = MSet<IdentityWrapper>()
    private inline fun <T> sync(block: MSet<IdentityWrapper>.() -> T): T = synchronized(this) { block(set) }

    fun add(element: ChunkBuffer) = sync { check(add(IdentityWrapper(element, Throwable()))) }
    fun remove(element: ChunkBuffer) = sync { check(remove(IdentityWrapper(element, null))) }
    fun clear() = sync { clear() }
    fun traces(): List<Throwable>? {
        val list = sync { values() }
        if (list.isEmpty()) return null
        return list.mapNotNull(IdentityWrapper::throwable)
    }
}

private class IdentityWrapper(
    private val instance: ChunkBuffer,
    val throwable: Throwable?
) {
    override fun equals(other: Any?): Boolean {
        if (other !is IdentityWrapper) return false
        return other.instance === this.instance
    }

    override fun hashCode(): Int = identityHashCode(instance)
}

expect fun identityHashCode(instance: Any): Int

expect class MSet<T>() {
    fun add(element: T): Boolean
    fun remove(element: T): Boolean
    fun clear()
    fun values(): List<T>
}
