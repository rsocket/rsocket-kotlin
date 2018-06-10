package io.rsocket.kotlin.internal.lease

import io.reactivex.Completable
import java.nio.ByteBuffer

interface LeaseGranter {

    fun grantLease(requests: Int, ttl: Int,
                   metadata: ByteBuffer?): Completable
}
