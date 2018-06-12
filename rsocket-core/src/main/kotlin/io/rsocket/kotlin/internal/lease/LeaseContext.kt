package io.rsocket.kotlin.internal.lease

/** State shared by Lease related interceptors  */
internal class LeaseContext(var leaseEnabled: Boolean = true) {

    override fun toString(): String =
            "LeaseContext{leaseEnabled=$leaseEnabled}"
}
