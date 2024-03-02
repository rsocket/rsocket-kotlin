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

package io.rsocket.kotlin.test

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import io.rsocket.kotlin.internal.io.*
import kotlinx.atomicfu.locks.*
import kotlin.test.*

val InUseTrackingPool: BufferPool = BufferPool(InUseTrackingPoolInstance)

private object InUseTrackingPoolInstance : ObjectPool<ChunkBuffer>, SynchronizedObject() {
    override val capacity: Int get() = BufferPool.capacity
    private val inUse = mutableSetOf<IdentityWrapper>()

    override fun borrow(): ChunkBuffer {
        val instance = BufferPool.borrow()
        synchronized(this) {
            check(inUse.add(IdentityWrapper(instance, Throwable())))
        }
        return instance
    }

    override fun recycle(instance: ChunkBuffer) {
        synchronized(this) {
            check(inUse.remove(IdentityWrapper(instance, null)))
        }
        BufferPool.recycle(instance)
    }

    override fun dispose() {
        BufferPool.dispose()
    }

    fun resetInUse() {
        synchronized(this) {
            inUse.clear()
        }
    }

    fun assertNoInUse() {
        val traces = synchronized(this) {
            inUse.mapNotNull(IdentityWrapper::throwable)
        }
        if (traces.isEmpty()) return
        fail(traceMessage(traces))
    }

    private fun traceMessage(traces: List<Throwable>): String = traces
        .groupBy(Throwable::stackTraceToString)
        .mapKeys { alignStackTrace(it.key) }
        .mapValues { it.value.size }
        .toList()
        .joinToString("\n", "Buffers in use[${traces.size}]:\n", "\n") { (stack, amount) ->
            "Buffer creation trace ($amount the same):\n$stack"
        }

    private fun alignStackTrace(trace: String): String {
        val stack = trace.split("\n").drop(1) // drops first empty trace
        val filtered =
            stack
                .filterNot { it.contains("TrackingSet") || it.contains("InUseTrackingPool") } //filter not relevant
                .filter {
                    //works on K/JVM and K/N
                    //on K/JS works only for legacy + node
                    it.contains("io.rsocket.kotlin") || it.contains("rsocket-kotlin/rsocket")
                }.ifEmpty { stack } //fallback to full stacktrace on unsupported K/JS variants

        //replaces `at` with `->` because on all platforms except K/JVM such strings are interpreted as stacktrace and merged
        return filtered.joinToString("\n  ->", "  ->") { it.substringAfter("at") }
    }

    //uses internal ktor APIs -
    // after ktor 1.5.3 it's the only way to make sure,
    // that there are no leaked buffers
    // used only on tests, so it's more or less safe
    // copy of io.ktor.utils.io.core.DefaultBufferPool with changed parent pool!!!
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private object BufferPool : DefaultPool<ChunkBuffer>(1000) {
        override fun produceInstance(): ChunkBuffer {
            return ChunkBuffer(DefaultAllocator.alloc(DEFAULT_BUFFER_SIZE), null, InUseTrackingPoolInstance)
        }

        override fun disposeInstance(instance: ChunkBuffer) {
            DefaultAllocator.free(instance.memory)
            super.disposeInstance(instance)
            instance.unlink()
        }

        override fun validateInstance(instance: ChunkBuffer) {
            super.validateInstance(instance)

            check(instance !== Buffer.Empty) { "Empty instance couldn't be recycled" }
            check(instance !== ChunkBuffer.Empty) { "Empty instance couldn't be recycled" }

            check(instance.referenceCount == 0) { "Unable to clear buffer: it is still in use." }
            check(instance.next == null) { "Recycled instance shouldn't be a part of a chain." }
            check(instance.origin == null) { "Recycled instance shouldn't be a view or another buffer." }
        }

        override fun clearInstance(instance: ChunkBuffer): ChunkBuffer {
            return super.clearInstance(instance).apply {
                unpark()
                reset()
            }
        }
    }
}

private class IdentityWrapper(
    private val instance: ChunkBuffer,
    val throwable: Throwable?,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is IdentityWrapper) return false
        return other.instance === this.instance
    }

    override fun hashCode(): Int = identityHashCode(instance)
}

//TODO leak tracking don't work on JS in SuspendTest
// due to `hack`, that to run `suspend` tests on JS, we return `Promise`, for which kotlin JS test runner will subscribe
// in such cases `AfterTest` will be called just after `Promise` returned, and not when it resolved
interface TestWithLeakCheck {

    @BeforeTest
    fun resetInUse() {
        InUseTrackingPoolInstance.resetInUse()
    }

    @AfterTest
    fun checkNoBuffersInUse() {
        InUseTrackingPoolInstance.assertNoInUse()
    }
}
