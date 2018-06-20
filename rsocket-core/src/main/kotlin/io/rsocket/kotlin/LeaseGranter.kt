package io.rsocket.kotlin

import io.reactivex.Completable
import java.nio.ByteBuffer

/**
 * Grants Lease to its peer
 */
interface LeaseGranter {

    fun grantLease(
            numberOfRequests: Int,
            ttlSeconds: Int,
            metadata: ByteBuffer): Completable

    fun grantLease(numberOfRequests: Int,
                   ttlSeconds: Int): Completable

    fun onClose(): Completable
}