package io.rsocket.kotlin

import io.reactivex.Completable
import java.nio.ByteBuffer

/** Provides means to grant lease to peer  */
interface LeaseRef {

    fun grantLease(
            numberOfRequests: Int,
            ttlMillis: Long,
            metadata: ByteBuffer): Completable

    fun grantLease(numberOfRequests: Int,
                   timeToLiveMillis: Long): Completable

    fun onClose(): Completable
}