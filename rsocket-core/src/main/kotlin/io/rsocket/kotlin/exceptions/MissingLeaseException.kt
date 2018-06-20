package io.rsocket.kotlin.exceptions

import io.rsocket.kotlin.Lease

class MissingLeaseException(lease: Lease, tag: String)
    : RejectedException(leaseMessage(lease, tag)) {

    override fun fillInStackTrace(): Throwable = this

    companion object {
        internal fun leaseMessage(lease: Lease, tag: String): String {
            val expired = lease.isExpired
            val allowedRequests = lease.allowedRequests
            return "$tag: Missing lease. " +
                    "Expired: $expired, allowedRequests: $allowedRequests"
        }
    }
}
