package io.rsocket.kotlin.internal.lease

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

internal class LeaseImpl(private val startingNumberOfRequests: Int,
                         override val ttl: Int,
                         override val metadata: ByteBuffer?) : Lease {
    private val numberOfRequests: AtomicInteger
    private val expiry: Long

    init {
        assertNumberOfRequests(startingNumberOfRequests, ttl)
        this.numberOfRequests = AtomicInteger(startingNumberOfRequests)
        this.expiry = now() + ttl
    }

    override val allowedRequests: Int
        get() = Math.max(0, numberOfRequests.get())

    override val isValid: Boolean
        get() = startingNumberOfRequests > 0
                && allowedRequests > 0
                && !isExpired

    override fun expiry(): Long {
        return expiry
    }

    fun availability(): Double {
        return if (isValid) allowedRequests /
                startingNumberOfRequests.toDouble() else 0.0
    }

    fun use(useRequestCount: Int): Boolean {
        assertUseRequests(useRequestCount)
        return !isExpired && numberOfRequests.accAndGet(
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

        fun invalidLease(): LeaseImpl = LeaseImpl(0, 0, null)

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
