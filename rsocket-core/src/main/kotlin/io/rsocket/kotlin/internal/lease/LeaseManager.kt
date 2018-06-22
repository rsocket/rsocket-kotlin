package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.Lease
import io.rsocket.kotlin.exceptions.MissingLeaseException
import java.nio.ByteBuffer

/**
 * Updates Lease on use and grant
 */
internal class LeaseManager(private val tag: String) {
    @Volatile
    private var currentLease = INVALID_MUTABLE_LEASE

    init {
        requireNotNull(tag, { "tag" })
    }

    fun availability(): Double = currentLease.availability()

    fun grant(numberOfRequests: Int, ttl: Int, metadata: ByteBuffer): Lease {
        assertGrantedLease(numberOfRequests, ttl)
        currentLease = LeaseImpl(numberOfRequests, ttl, metadata)
        return hide(currentLease)
    }

    fun use(): Result =
            if (currentLease.use(1))
                Success
            else
                Error(MissingLeaseException(currentLease, tag))

    private fun hide(l: Lease): Lease = DelegatingLease(l)

    private class DelegatingLease(l: Lease) : Lease by l

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