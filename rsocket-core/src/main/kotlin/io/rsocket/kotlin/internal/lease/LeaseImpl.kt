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
