package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.exceptions.MissingLeaseException

/** Updates Lease on use and grant  */
internal class LeaseManager(private val tag: String) {
    @Volatile
    private var currentLease = INVALID_MUTABLE_LEASE

    init {
        requireNotNull(tag, { "tag" })
    }

    fun availability(): Double {
        return currentLease.availability()
    }

    fun grantLease(numberOfRequests: Int, ttl: Int) {
        assertGrantedLease(numberOfRequests, ttl)
        this.currentLease = LeaseImpl(numberOfRequests, ttl, null)
    }

    fun useLease(): Result =
            if (currentLease.use(1))
                Success
            else
                Error(MissingLeaseException(currentLease, tag))

    override fun toString(): String {
        return "LeaseManager{tag='$tag'}"
    }

    companion object {
        private val INVALID_MUTABLE_LEASE = LeaseImpl.invalidLease()

        private fun assertGrantedLease(numberOfRequests: Int, ttl: Int) {
            if (numberOfRequests <= 0) {
                throw IllegalArgumentException("numberOfRequests must be positive")
            }
            if (ttl <= 0) {
                throw IllegalArgumentException("time-to-live must be positive")
            }
        }
    }
}

internal sealed class Result

internal object Success : Result()

internal data class Error(val ex: Throwable) : Result()