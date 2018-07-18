/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.Lease
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

internal class LeaseImpl(override val initialAllowedRequests: Int,
                         override val timeToLiveSeconds: Int,
                         override val metadata: ByteBuffer) : Lease {
    private val allowedReqs: AtomicInteger
    override val expiry: Long

    init {
        assertNumberOfRequests(initialAllowedRequests, timeToLiveSeconds)
        this.allowedReqs = AtomicInteger(initialAllowedRequests)
        this.expiry = now() + timeToLiveSeconds
    }

    override val allowedRequests: Int
        get() = Math.max(0, allowedReqs.get())

    override val isValid: Boolean
        get() = initialAllowedRequests > 0
                && allowedRequests > 0
                && !isExpired

    fun availability(): Double {
        return if (isValid) allowedRequests /
                initialAllowedRequests.toDouble() else 0.0
    }

    fun use(useRequestCount: Int): Boolean {
        assertUseRequests(useRequestCount)
        return !isExpired && allowedReqs.accAndGet(
                useRequestCount,
                { cur, update -> Math.max(-1, cur - update) }) >= 0
    }

    companion object {

        private fun now() = System.currentTimeMillis()

        private inline fun AtomicInteger.accAndGet(update: Int,
                                                   acc: (Int, Int) -> Int): Int {
            var cur: Int
            var next: Int
            do {
                cur = get()
                next = acc.invoke(cur, update)
            } while (!compareAndSet(cur, next))
            return next
        }

        fun invalidLease(): LeaseImpl = LeaseImpl(0,
                0,
                ByteBuffer.allocateDirect(0))

        fun assertUseRequests(useRequestCount: Int) {
            if (useRequestCount <= 0) {
                throw IllegalArgumentException("Number of requests must be positive")
            }
        }

        private fun assertNumberOfRequests(numberOfRequests: Int, ttl: Int) {
            if (numberOfRequests < 0) {
                throw IllegalArgumentException("Number of requests must be non-negative")
            }
            if (ttl < 0) {
                throw IllegalArgumentException("Time-to-live must be non-negative")
            }
        }
    }
}
