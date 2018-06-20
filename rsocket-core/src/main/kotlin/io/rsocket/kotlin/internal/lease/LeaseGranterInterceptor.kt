package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.LeaseSupport
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor

internal class LeaseGranterInterceptor(
        private val leaseContext: LeaseContext,
        private val sender: LeaseManager,
        private val receiver: LeaseManager,
        private val leaseHandle: (LeaseSupport) -> Unit)
    : DuplexConnectionInterceptor {

    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        connection: DuplexConnection): DuplexConnection {
        return if (type === DuplexConnectionInterceptor.Type.SERVICE) {
            val leaseConnection = LeaseConnection(
                    leaseContext,
                    connection,
                    sender,
                    receiver)
            val leaseSupport = LeaseSupport(leaseConnection)
            leaseHandle(leaseSupport)
            leaseConnection
        } else {
            connection
        }
    }
}
