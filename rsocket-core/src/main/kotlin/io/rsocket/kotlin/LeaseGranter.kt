package io.rsocket.kotlin

import io.reactivex.Completable
import java.nio.ByteBuffer

/**
 * Grants Lease to its peer
 */
interface LeaseGranter {
    /**
     * Grants lease to its peer
     *
     * @param numberOfRequests number of requests peer is allowed to perform.
     *                         Must be positive
     * @param ttlSeconds number of seconds that this lease is valid from the time
     * it is received.
     * @param metadata metadata associated with this lease grant
     * @return [Completable] which completes once Lease is sent
     */
    fun grantLease(
            numberOfRequests: Int,
            ttlSeconds: Int,
            metadata: ByteBuffer): Completable

    /**
     * Grants lease to its peer
     *
     * @param numberOfRequests number of requests peer is allowed to perform.
     *                         Must be positive
     * @param ttlSeconds number of seconds that this lease is valid from the time
     * it is received.
     * @return [Completable] which completes once Lease is sent
     */
    fun grantLease(numberOfRequests: Int,
                   ttlSeconds: Int): Completable

    /**
     * @return [Completable] which completes when associated RSocket is closed
     */
    fun onClose(): Completable
}