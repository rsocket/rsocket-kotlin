package io.rsocket.kotlin.internal.lease

import io.reactivex.Completable
import io.rsocket.kotlin.LeaseRef
import java.nio.ByteBuffer

internal class ConnectionLeaseRef(private val leaseGranterConnection
                                  : LeaseGranterConnection) : LeaseRef {

    override fun grantLease(numberOfRequests: Int,
                            ttlMillis: Long,
                            metadata: ByteBuffer): Completable {
        return grant(
                numberOfRequests,
                ttlMillis,
                metadata)
    }

    override fun grantLease(numberOfRequests: Int,
                            timeToLiveMillis: Long): Completable {
        return grant(
                numberOfRequests,
                timeToLiveMillis,
                null)
    }

    override fun onClose(): Completable = leaseGranterConnection.onClose()

    private fun grant(
            numberOfRequests: Int,
            ttlMillis: Long,
            metadata: ByteBuffer?): Completable {
        assertArgs(numberOfRequests, ttlMillis)
        val ttl = Math.toIntExact(ttlMillis)
        return leaseGranterConnection.grantLease(
                numberOfRequests,
                ttl,
                metadata)
    }

    private fun assertArgs(numberOfRequests: Int, ttl: Long) {
        if (numberOfRequests <= 0) {
            throw IllegalArgumentException(
                    "numberOfRequests must be non-negative: $numberOfRequests")
        }
        if (ttl <= 0) {
            throw IllegalArgumentException(
                    "timeToLive must be non-negative: $ttl")
        }
    }
}
