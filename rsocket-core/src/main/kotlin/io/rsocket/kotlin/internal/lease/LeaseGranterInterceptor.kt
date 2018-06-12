package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.LeaseRef
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor

internal class LeaseGranterInterceptor(
        private val leaseContext: LeaseContext,
        private val sender: LeaseManager,
        private val receiver: LeaseManager,
        private val leaseHandle: (LeaseRef) -> Unit)
    : DuplexConnectionInterceptor {

    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        connection: DuplexConnection): DuplexConnection {
        return if (type === DuplexConnectionInterceptor.Type.SERVICE) {
            val leaseGranterConnection = LeaseGranterConnection(
                    leaseContext,
                    connection,
                    sender,
                    receiver)
            val leaseConnectionRef = ConnectionLeaseRef(leaseGranterConnection)
            leaseHandle(leaseConnectionRef)
            leaseGranterConnection
        } else {
            connection
        }
    }
}
