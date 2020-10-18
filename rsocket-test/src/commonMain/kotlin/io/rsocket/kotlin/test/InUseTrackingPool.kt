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
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlin.test.*

object InUseTrackingPool : ObjectPool<ChunkBuffer> {
    override val capacity: Int get() = ChunkBuffer.Pool.capacity
    private val inUse = atomic(0)

    override fun borrow(): ChunkBuffer {
        inUse.incrementAndGet()
        return ChunkBuffer.Pool.borrow()
    }

    override fun recycle(instance: ChunkBuffer) {
        inUse.decrementAndGet()
        ChunkBuffer.Pool.recycle(instance)
    }

    override fun dispose() {
        ChunkBuffer.Pool.dispose()
    }

    fun resetInUse() {
        inUse.lazySet(0)
    }

    fun assertNoInUse() {
        val v = inUse.value
        assertEquals(0, v, "Buffers in use")
    }
}

interface TestWithLeakCheck {

    @BeforeTest
    fun resetInUse() {
        InUseTrackingPool.resetInUse()
    }

    @AfterTest
    fun checkNoBuffersInUse() {
        InUseTrackingPool.assertNoInUse()
    }
}
